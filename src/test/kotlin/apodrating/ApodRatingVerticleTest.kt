package apodrating

import apodrating.mock.MockServiceVerticle
import apodrating.model.ApodRatingConfiguration
import apodrating.model.ApodRequest
import apodrating.model.RatingRequest
import apodrating.model.asApod
import apodrating.model.asRating
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
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

private const val LOCALHOST: String = "localhost"

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

    /**
     * Prepare test by deploying the verticle.
     */
    @BeforeAll
    @DisplayName("Deploy verticle ")
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        ConfigRetriever
            .create(
                Vertx.vertx(), configRetrieverOptionsOf()
                    .addStore(
                        configStoreOptionsOf(
                            type = "file",
                            format = "properties",
                            config = JsonObject()
                                .put("path", "test.properties")
                        )
                    )
            )
            .configStream()
            .handler { config ->
                Single.zip<String, List<String>>(
                    listOf(
                        vertx.rxDeployVerticle(MockServiceVerticle()),
                        vertx.rxDeployVerticle(
                            ApodRatingVerticle(),
                            DeploymentOptions(JsonObject().put("config", config))
                        ),
                        vertx.rxDeployVerticle(
                            ApodRemoteProxyVerticle(),
                            DeploymentOptions(JsonObject().put("config", config))
                        )
                    )
                ) { it.filterIsInstance<String>() }
                    .doAfterSuccess { testConfig = ApodRatingConfiguration(config) }
                    .doAfterSuccess { webClient = WebClient.create(vertx) }
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                        testContext.completeNow()
                    }) { error -> logger.error { error } }

            }
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
            .map { it.body() }
            .map { asApod(it).id }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`("0")) }
            .doOnSuccess { logger.info { "rxGetApod/o succeeded" } }
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
            .map { it.body() }
            .map { VertxMatcherAssert.assertThat(testContext, it.size(), Matchers.`is`(4)) }
            .doOnSuccess { logger.info { "rxGetApod succeeded" } }
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
            .map { it.statusCode() }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(HttpStatus.SC_CREATED)) }
            .doOnSuccess { logger.info { "rxPostApod succeeded" } }
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
            .map { it.statusCode() }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(HttpStatus.SC_NO_CONTENT)) }
            .doOnSuccess { logger.info { "rxPUTrating succeeded" } }
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
            .map { it.body() }
            .map { asRating(it).id }
            .map { VertxMatcherAssert.assertThat(testContext, it, Matchers.`is`(0)) }
            .doOnSuccess { logger.info { "rxGETrating succeeded" } }
            .subscribe({ testContext.completeNow() }) { testContext.failNow(it) }
    }

    /**
     * Undeply Verticle.
     */
    @AfterAll
    @DisplayName("Undeploy verticle ")
    fun undeployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        Single.just(vertx.deploymentIDs())
            .flattenAsFlowable { it }
            .parallel().runOn(Schedulers.computation())
            .map {
                logger.info { "undeploying $it" }
                it
            }
            .map { vertx.rxUndeploy(it) }
            .sequential()
            .toList()
            .doOnSuccess { webClient.close() }
            .doAfterSuccess { testContext.completeNow() }
            .subscribeOn(Schedulers.computation())
            .subscribe({ logger.info { "undeployed ${it.size} verticles" } }, { testContext.failNow(it) })
    }
}

