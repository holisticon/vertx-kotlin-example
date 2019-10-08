package apodrating.remoteproxy

import apodrating.CACHE_ALIAS
import apodrating.CIRCUIT_BREAKER_NAME
import apodrating.FIELD_DATE
import apodrating.PARAM_API_KEY
import apodrating.PARAM_HD
import apodrating.model.Apod
import apodrating.model.ApodRatingConfiguration
import apodrating.model.asApod
import apodrating.model.emptyApod
import apodrating.model.isEmpty
import apodrating.model.toJsonObject
import apodrating.webapi.handleFailure
import com.hazelcast.cache.ICache
import com.hazelcast.config.CacheSimpleConfig
import com.hazelcast.config.EvictionConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.ICacheManager
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toCompletable
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.circuitbreaker.circuitBreakerOptionsOf
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate
import io.vertx.reactivex.ext.web.codec.BodyCodec
import io.vertx.serviceproxy.ServiceException
import io.vertx.spi.cluster.hazelcast.ConfigUtil
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import mu.KLogging
import org.apache.http.HttpStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service implementation to access the remote NASA apod api.
 */
class RemoteProxyServiceImpl(
    val vertx: Vertx, val config: JsonObject,
    private val apodConfig: ApodRatingConfiguration = ApodRatingConfiguration(config)
) : RemoteProxyService {

    companion object : KLogging()

    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var webClient: WebClient
    private lateinit var cache: ICache<String, Apod>

    init {
        val hazelcastConf = {
            val hazelcastConfig = ConfigUtil.loadConfig()
            val cacheConfig = CacheSimpleConfig()
            cacheConfig.name = CACHE_ALIAS
            cacheConfig.evictionConfig.size = apodConfig.cacheSize
            cacheConfig.evictionConfig.maximumSizePolicy = EvictionConfig.MaxSizePolicy.ENTRY_COUNT
            cacheConfig.expiryPolicyFactoryConfig = CacheSimpleConfig.ExpiryPolicyFactoryConfig(
                CacheSimpleConfig.ExpiryPolicyFactoryConfig.TimedExpiryPolicyFactoryConfig(
                    CacheSimpleConfig
                        .ExpiryPolicyFactoryConfig.TimedExpiryPolicyFactoryConfig.ExpiryPolicyType.CREATED,
                    CacheSimpleConfig.ExpiryPolicyFactoryConfig.DurationConfig(
                        apodConfig.cacheLifetimeMinutes,
                        TimeUnit.MINUTES
                    )
                )
            )
            hazelcastConfig.addCacheConfig(cacheConfig)
            val hz: HazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig)
            val cacheManager: ICacheManager = hz.cacheManager
            val mgr = HazelcastClusterManager(hazelcastConfig)
            cache = cacheManager.getCache(CACHE_ALIAS)
            val options = VertxOptions().setClusterManager(mgr)
            Vertx.rxClusteredVertx(options)
                .subscribe { onSuccess: Vertx?, throwable: Throwable? ->
                    throwable?.let { logger.error { it.toString() } }
                        ?: logger.info { "clustered vertx started: ${onSuccess?.isClustered}" }
                }
        }.toCompletable().subscribeOn(Schedulers.io())

        val circuitbreakerCompletable = {
            circuitBreaker = CircuitBreaker.create(
                CIRCUIT_BREAKER_NAME, vertx,
                circuitBreakerOptionsOf(
                    maxFailures = 3, // number of failures before opening the circuit
                    timeout = 2000L, // consider a failure if the operation does not succeed in time
                    fallbackOnFailure = true, // do we call the fallback on failure
                    resetTimeout = 1000, // time spent in open state before attempting to re-try
                    maxRetries = 3 // the number of times the circuit breaker tries to redo the
                    // operation before failing
                )
            )
        }.toCompletable().subscribeOn(Schedulers.io())

        val webClientCompletable = {
            webClient = WebClient.create(vertx)
        }.toCompletable().subscribeOn(Schedulers.io())
        Completable.mergeArray(circuitbreakerCompletable, webClientCompletable, hazelcastConf)
            .blockingAwait()
    }

    override fun performApodQuery(
        id: String,
        date: String,
        nasaApiKey: String,
        resultHandler: Handler<AsyncResult<JsonObject>>
    ): RemoteProxyService {
        getFromCacheOrRemoteApi(id, date, nasaApiKey)
            .filter { it.isEmpty().not() }
            .map { Future.succeededFuture(it.toJsonObject()) }
            .switchIfEmpty(maybeOfFailedFuture())
            .subscribeOn(Schedulers.io())
            .subscribe(resultHandler::handle) {
                handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
            }
        return this
    }

    private fun getFromCacheOrRemoteApi(id: String, date: String, nasaApiKey: String): Single<Apod> = Maybe
        .fromFuture(cache.getAsync(date))
        .switchIfEmpty(with(AtomicInteger(), {
            circuitBreaker.rxExecuteWithFallback({ future ->
                if (getAndIncrement() > 0)
                    logger.info { "number of retries: ${get() - 1}" }
                rxSendGet(date, nasaApiKey, id)
                    .subscribeOn(Schedulers.io())
                    .flatMap { apod ->
                        Single.fromFuture(cache.putIfAbsentAsync(date, apod))
                            .map { newlyAddedToCache -> Pair(apod, newlyAddedToCache) }
                    }
                    .subscribe({
                        future.complete(it.first)
                    }) { future.fail(it) }
            }) {
                logger.error { "Circuit opened. Error: $it - message: ${it.message}" }
                emptyApod()
            }
        }))

    private fun rxSendGet(date: String, nasaApiKey: String, apodId: String): Single<Apod> =
        webClient.getAbs(apodConfig.nasaApiHost)
            .uri(apodConfig.nasaApiPath)
            .addQueryParam(FIELD_DATE, date)
            .addQueryParam(PARAM_API_KEY, nasaApiKey)
            .addQueryParam(PARAM_HD, true.toString())
            .expect(ResponsePredicate.SC_SUCCESS)
            .expect(ResponsePredicate.JSON)
            .`as`(BodyCodec.jsonObject())
            .rxSend()
            .map { it.body() }
            .map { asApod(apodId, it) }

    private fun maybeOfFailedFuture(): Maybe<Future<JsonObject>> {
        return Maybe.just(
            Future.failedFuture(
                ServiceException(
                    HttpStatus.SC_NOT_FOUND,
                    "not found"
                )
            )
        )
    }

    /**
     * close resources
     */
    override fun close() {
        webClient.close()
        circuitBreaker.close()
        cache.clear()
    }
}