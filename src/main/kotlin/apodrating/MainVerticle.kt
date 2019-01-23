package apodrating

import apodrating.model.deploymentOptionsFromEnv
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.reactivex.core.AbstractVerticle
import mu.KLogging

/**
 * Start all project verticles.
 */
class MainVerticle : AbstractVerticle() {
    companion object : KLogging()

    /**
     * Start the MainVerticle and our two application verticles.
     */
    override fun start() {
        val startupTime = System.currentTimeMillis()
        Single.zip<String, List<String>>(
            listOf(
                vertx.rxDeployVerticle(ApodRatingVerticle::class.java.canonicalName, deploymentOptionsFromEnv(vertx)),
                vertx.rxDeployVerticle(
                    ApodRemoteProxyVerticle::class.java.canonicalName,
                    deploymentOptionsFromEnv(vertx)
                )
            )
        ) { it.filterIsInstance<String>() }
            .subscribeOn(Schedulers.computation())
            .subscribe({ verticles ->
                logger.info {
                    "Started MainVerticle with ${verticles.size} child " +
                        "verticles: $verticles in ${System.currentTimeMillis() - startupTime}ms"
                }
            }) {
                logger.error { it }
            }
    }
}