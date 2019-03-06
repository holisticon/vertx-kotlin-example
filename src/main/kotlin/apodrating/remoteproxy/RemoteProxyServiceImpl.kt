package apodrating.remoteproxy

import apodrating.CACHE_ALIAS
import apodrating.CIRCUIT_BREAKER_NAME
import apodrating.FIELD_DATE
import apodrating.HEAP_POOL_SIZE
import apodrating.PARAM_API_KEY
import apodrating.PARAM_HD
import apodrating.model.Apod
import apodrating.model.ApodRatingConfiguration
import apodrating.model.asApod
import apodrating.model.emptyApod
import apodrating.model.isEmpty
import apodrating.model.toJsonObject
import apodrating.webapi.handleFailure
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.circuitbreaker.circuitBreakerOptionsOf
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate
import io.vertx.reactivex.ext.web.codec.BodyCodec
import io.vertx.serviceproxy.ServiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.apache.http.HttpStatus
import org.ehcache.Cache
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service implementation to access the remote NASA apod api.
 */
class RemoteProxyServiceImpl(
    vertx: Vertx, val config: JsonObject,
    private val apodConfig: ApodRatingConfiguration = ApodRatingConfiguration(config)
) : RemoteProxyService {

    companion object : KLogging()

    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var webClient: WebClient
    private lateinit var apodCache: Cache<String, Apod>

    init {
        runBlocking {
            val cacheRoutine = async {
                CacheManagerBuilder.newCacheManagerBuilder()
                    .withCache(
                        CACHE_ALIAS,
                        CacheConfigurationBuilder
                            .newCacheConfigurationBuilder(
                                String::class.java,
                                Apod::class.java,
                                ResourcePoolsBuilder.heap(HEAP_POOL_SIZE)
                            )
                    ).build()
                    .apply {
                        this.init()
                        apodCache = this.getCache(
                            CACHE_ALIAS,
                            String::class.java,
                            Apod::class.java
                        )
                    }
            }

            val circuitbreakerRoutine = async {
                circuitBreaker = CircuitBreaker.create(
                    CIRCUIT_BREAKER_NAME, vertx,
                    circuitBreakerOptionsOf(
                        maxFailures = 3, // number of failures before opening the circuit
                        timeout = 2000L, // consider a failure if the operation does not succeed in time
                        fallbackOnFailure = true, // do we call the fallback on failure
                        resetTimeout = 1000, // time spent in open state before attempting to re-try
                        maxRetries = 3 // the number of times the circuit breaker tries to redo the operation before failing
                    )
                )
            }

            val webClientRoutine = async {
                webClient = WebClient.create(vertx)
            }

            cacheRoutine.await()
            circuitbreakerRoutine.await()
            webClientRoutine.await()
        }
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
            .switchIfEmpty(
                Maybe.just(
                    Future.failedFuture(
                        ServiceException(
                            HttpStatus.SC_NOT_FOUND,
                            "not found"
                        )
                    )
                )
            )
            .subscribeOn(Schedulers.io())
            .subscribe(resultHandler::handle) {
                handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
            }
        return this
    }

    private fun getFromCacheOrRemoteApi(id: String, date: String, nasaApiKey: String): Single<Apod> = when {
        apodCache.containsKey(date) -> Single
            .just(apodCache.get(date))
            .doOnSuccess { logger.info { "cache hit: $id" } }
        else -> with(AtomicInteger()) {
            circuitBreaker.rxExecuteCommandWithFallback<Apod>({ future ->
                if (this.getAndIncrement() > 0)
                    logger.info { "number of retries: ${this.get() - 1}" }
                rxSendGet(date, nasaApiKey, id)
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess {
                        if (!apodCache.containsKey(date)) {
                            apodCache.put(date, it)
                            logger.info { "added entry to cache: ${it.id}" }
                        }
                    }.subscribe({ future.complete(it) }) { future.fail(it) }
            }) {
                logger.error { "Circuit opened. Error: $it - message: ${it.message}" }
                emptyApod()
            }
        }

    }

    private fun rxSendGet(date: String, nasaApiKey: String, apodId: String): Single<Apod> =
        runBlocking(Dispatchers.IO) { webClient.getAbs(apodConfig.nasaApiHost) }
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

    /**
     * close resources
     */
    override fun close() {
        webClient.close()
        circuitBreaker.close()
        apodCache.clear()
    }
}