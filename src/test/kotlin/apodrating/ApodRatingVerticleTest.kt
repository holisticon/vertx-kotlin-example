package apodrating

import apodrating.mock.MockServiceVerticle
import apodrating.model.ApodRatingConfiguration
import apodrating.model.ApodRequest
import apodrating.model.RatingRequest
import apodrating.model.asApod
import apodrating.model.asRating
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.junit.Repeat
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.codec.BodyCodec
import mu.KLogging
import org.apache.http.HttpStatus
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

private const val LOCALHOST: String = "localhost"

private const val RETRY_COUNT: Long = 5

private const val DELAY_MS: Long = 100

/**
 * Unit test for ApodRatingVerticle
 */
@DisplayName("👋 A fairly basic test example")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ApodRatingVerticleTest {

    companion object : KLogging()

    private lateinit var testConfig: ApodRatingConfiguration

    private lateinit var webClient: WebClient

    private fun verticlesToBeDeployed(
        vertx: Vertx,
        config: JsonObject
    ): Flowable<Single<String>> = Flowable
        .fromIterable(
            listOf(
                vertx.rxDeployVerticle(
                    MockServiceVerticle(),
                    DeploymentOptions(JsonObject().put("config", config))
                ),
                vertx.rxDeployVerticle(
                    ApodRatingVerticle(),
                    DeploymentOptions(JsonObject().put("config", config))
                )
            )
        ).subscribeOn(Schedulers.computation())

    private fun configRetrieverOptions(): ConfigRetrieverOptions =
        configRetrieverOptionsOf()
            .addStore(
                configStoreOptionsOf(
                    type = "file",
                    format = "properties",
                    config = JsonObject()
                        .put("path", "test.properties")
                )
            )

    /**
     * Prepare test by deploying the verticle.
     */
    @BeforeAll
    @DisplayName("Deploy verticles ")
    fun deployVerticles(vertx: Vertx, testContext: VertxTestContext) {
        ConfigRetriever
            .create(vertx, configRetrieverOptions())
            .rxGetConfig().observeOn(Schedulers.computation())
            .toFlowable().doAfterNext { testConfig = ApodRatingConfiguration(it) }
            .flatMap { verticlesToBeDeployed(vertx, it) }
            .parallel().runOn(Schedulers.computation())
            .flatMap { it.toFlowable() }
            .sequential().toList()
            .doOnSuccess { webClient = WebClient.create(vertx) }
            .subscribeOn(Schedulers.computation())
            .subscribe({ testContext.completeNow() }) { error -> logger.error { error } }
    }

    /**
     * Perform test.
     */
    @DisplayName("rx GET apod/0")
    @Test
    @Order(1)
    fun rxGetApod(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "rx GET apod" }
        webClient.get(testConfig.port, LOCALHOST, "/apod/0")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .`as`(BodyCodec.jsonObject())
            .rxSend()
            .retryWhen { throwableOccurs(it) }
            .map { it.body() }
            .map { asApod(it).id }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`("0")) }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    /**
     * Perform test.
     */
    @DisplayName("rx GET apod")
    @Test
    @Repeat(2)
    @Order(2)
    fun getApods(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "rx GET apods" }
        webClient.get(testConfig.port, LOCALHOST, "/apod")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .`as`(BodyCodec.jsonArray())
            .rxSend()
            .retryWhen { throwableOccurs(it) }
            .map { it.body() }
            .map { VertxMatcherAssert.assertThat(testContext, it.size(), Matchers.`is`(10)) }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    /**
     * Perform test.
     */
    @DisplayName("rx POST apod")
    @Test
    @Order(3)
    fun postApod(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "rx POST apod" }
        webClient
            .post(testConfig.port, LOCALHOST, "/apod")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .rxSendJson(JsonObject.mapFrom(ApodRequest("2018-02-02")))
            .retryWhen { throwableOccurs(it) }
            .map { it.statusCode() }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(HttpStatus.SC_CREATED)) }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    /**
     * Perform test.
     */
    @DisplayName("rx PUT rating")
    @Test
    @Order(5)
    fun putRating(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "rx PUT rating" }
        webClient
            .put(testConfig.port, LOCALHOST, "/apod/0/rating")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .rxSendJsonObject(JsonObject.mapFrom(RatingRequest(7)))
            .retryWhen { throwableOccurs(it) }
            .map { it.statusCode() }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(HttpStatus.SC_NO_CONTENT)) }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    /**
     * Perform test.
     */
    @DisplayName("rx GET apod/0/rating")
    @Test
    @Order(4)
    fun getApodRating(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "rx GET rating" }
        webClient
            .get(testConfig.port, LOCALHOST, "/apod/0/rating")
            .`as`(BodyCodec.jsonObject())
            .rxSend()
            .retryWhen { throwableOccurs(it) }
            .map { it.body() }
            .map { asRating(it).id }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(0)) }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    private fun throwableOccurs(throwable: Flowable<Throwable>) =
        throwable
            .take(RETRY_COUNT)
            .delay(DELAY_MS, TimeUnit.MILLISECONDS)

    /**
     * Undeply Verticle.
     */
    @AfterAll
    @DisplayName("Undeploy verticle ")
    fun undeployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        Single.just(vertx.deploymentIDs())
            .flattenAsFlowable { it }
            .parallel().runOn(Schedulers.computation())
            .map { vertx.rxUndeploy(it) }
            .sequential()
            .toList()
            .doOnSuccess { webClient.close() }
            .doAfterSuccess { testContext.completeNow() }
            .subscribeOn(Schedulers.computation())
            .subscribe({ logger.info { "undeployed ${it.size} verticles" } }, { testContext.failNow(it) })
    }
}

