package apodrating

import apodrating.model.deploymentOptionsFromEnv
import io.reactivex.Single
import io.vertx.reactivex.core.AbstractVerticle
import mu.KLogging

class MainVerticle : AbstractVerticle() {
    companion object : KLogging()

    /**
     * Start the MainVerticle and our two application verticles.
     */
    override fun start() {
        Single.zip<String, List<String>>(
            listOf(
                vertx.rxDeployVerticle(ApodRatingVerticle(), deploymentOptionsFromEnv(vertx)),
                vertx.rxDeployVerticle(ApodRemoteProxyVerticle(), deploymentOptionsFromEnv(vertx))
            )
        ) { verticles ->
            verticles
                .filter { it is String }
                .map { it as String }
        }
            .subscribe({ verticles ->
                logger.info { "Started MainVerticle with ${verticles.size} depending verticles: $verticles" }
            }) {
                logger.error { it }
            }
    }
}