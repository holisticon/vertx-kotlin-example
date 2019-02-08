package apodrating

import io.reactivex.Single
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.codec.BodyCodec
import mu.KLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Unit test for ApodRatingVerticle
 */
@DisplayName("👋 A fairly basic test example")
@ExtendWith(VertxExtension::class)
class ApodRatingVerticleTest {

    companion object : KLogging()

    /**
     * Prepare test by deploying the verticle.
     */
    @BeforeEach
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
                    .subscribe({
                        testContext.completeNow()
                    }) { error -> logger.error { error } }
            }
    }

    /**
     * Perform test.
     */
    @DisplayName("Get asRating")
    @Test
    fun getApod(vertx: Vertx, testContext: VertxTestContext) {
        val client = WebClient.create(vertx)
        client.get(8081, "localhost", "/apod/0")
            .`as`(BodyCodec.jsonObject())
            .send { ar ->
                when {
                    ar.succeeded() -> {
                        logger.info {
                            "success! ${ar.result()}"
                            testContext.completeNow()
                        }
                    }
                    else -> testContext.failNow(ar.cause())
                }
            }
    }

    /**
     * Undeply Verticle.
     */
    @AfterEach
    @DisplayName("Undeploy verticle ")
    fun undeployVerticle(vertx: Vertx) {
        vertx.undeploy("apodrating.ApodRatingVerticle") {
            when {
                it.succeeded() -> logger.info { "undeployed" }
                else -> logger.error { "undeployment failed" }
            }
        }
    }
}

