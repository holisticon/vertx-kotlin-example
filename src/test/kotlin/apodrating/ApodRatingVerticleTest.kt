package apodrating

import apodrating.mock.MockServiceVerticle
import apodrating.model.ApodRatingConfiguration
import apodrating.model.ApodRequest
import apodrating.model.Rating
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
                        )
                    )
                ) { it.filterIsInstance<String>() }
                    .doAfterSuccess {
                        testConfig = ApodRatingConfiguration(config)
                    }
                    .subscribe({
                        testContext.completeNow()
                    }) { error -> logger.error { error } }

            }
    }

    /**
     * Perform test.
     */
    @DisplayName("GET apod/0")
    @Test
    @Order(1)
    fun getApod(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "GET apod" }
        WebClient.create(vertx).get(testConfig.port, LOCALHOST, "/apod/0")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .`as`(BodyCodec.jsonObject())
            .send { ar ->
                when {
                    ar.succeeded() -> {
                        VertxMatcherAssert.assertThat(testContext, asApod(ar.result().body()).id, Matchers.`is`("0"))
                        testContext.completeNow()
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
    }

    /**
     * Perform test.
     */
    @DisplayName("GET apod")
    @Test
    @Repeat(2)
    @Order(2)
    fun getApods(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "GET apods" }
        WebClient.create(vertx).get(testConfig.port, LOCALHOST, "/apod")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .`as`(BodyCodec.jsonArray())
            .send { ar ->
                when {
                    ar.succeeded() -> logger.info {
                        val result = ar.result().body()
                        VertxMatcherAssert.assertThat(testContext, result.size(), Matchers.`is`(4))
                        testContext.completeNow()
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
    }

    /**
     * Perform test.
     */
    @DisplayName("POST apod")
    @Test
    @Order(3)
    fun postApod(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "POST apod" }
        WebClient.create(vertx).post(testConfig.port, LOCALHOST, "/apod")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .sendJsonObject(JsonObject.mapFrom(ApodRequest("2018-02-02"))) { ar ->
                when {
                    ar.succeeded() -> {
                        VertxMatcherAssert.assertThat(
                            testContext,
                            ar.result().statusCode(),
                            Matchers.`is`(HttpStatus.SC_CREATED)
                        )
                        testContext.completeNow()
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
    }

    /**
     * Perform test.
     */
    @DisplayName("PUT rating")
    @Test
    @Order(5)
    fun putRating(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "PUT rating" }
        WebClient.create(vertx).put(testConfig.port, LOCALHOST, "/apod/0/rating")
            .putHeader(API_KEY_HEADER, testConfig.nasaApiKey)
            .sendJsonObject(JsonObject.mapFrom(RatingRequest(7))) { ar ->
                when {
                    ar.succeeded() -> {
                        VertxMatcherAssert.assertThat(
                            testContext,
                            ar.result().statusCode(),
                            Matchers.`is`(HttpStatus.SC_NO_CONTENT)
                        )
                        testContext.completeNow()
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
    }

    /**
     * Perform test.
     */
    @DisplayName("GET apod/0/rating")
    @Test
    @Order(4)
    fun getApodRating(vertx: Vertx, testContext: VertxTestContext) {
        logger.info { "GET rating" }
        WebClient.create(vertx).get(testConfig.port, LOCALHOST, "/apod/0/rating")
            .`as`(BodyCodec.jsonObject())
            .send { ar ->
                when {
                    ar.succeeded() -> {
                        val rating: Rating = asRating(ar.result().body())
                        VertxMatcherAssert.assertThat(testContext, rating.id, Matchers.`is`(0))
                        testContext.completeNow()
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
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
            .map {
                vertx.rxUndeploy(it)
            }.sequential()
            .toList()
            .doAfterSuccess {
                testContext.completeNow()
            }
            .subscribe({
                logger.info { "undeployed ${it.size} verticles" }

            },
                {
                    testContext.failNow(it)
                })
    }
}

