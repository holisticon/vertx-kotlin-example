package apodrating

import io.vertx.core.DeploymentOptions
import io.vertx.kotlin.config.ConfigRetrieverOptions
import io.vertx.kotlin.config.ConfigStoreOptions
import io.vertx.reactivex.core.Vertx.vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(): Unit =
    vertx().rxDeployVerticle(ApodRatingVerticle::class.java.canonicalName, deploymentOptions())
        .subscribe({ logger.info { "succeeded: $it" } }) { logger.error { it } }
        .dispose()

private fun deploymentOptions(): DeploymentOptions {
    return ConfigRetrieverOptions(
        scanPeriod = 2000,
        stores = listOf(ConfigStoreOptions(type = "env"))
    ).deploymentOptions(vertx())
}

