package apodrating

import apodrating.model.deploymentOptionsFromEnv
import io.vertx.reactivex.core.AbstractVerticle
import mu.KLogging

private val verticleName: String = ApodRatingVerticle::class.java.canonicalName

/**
 * Start all project verticles.
 */
class MainVerticle : AbstractVerticle() {
    companion object : KLogging()

    /**
     * Start the MainVerticle and our two application verticles.
     */
    override fun start() {
        with(System.currentTimeMillis()) {
            deploymentOptionsFromEnv(vertx)
                .flatMap { vertx.rxDeployVerticle(verticleName, it) }
                .subscribe({ logger.info { "Startup time ${System.currentTimeMillis() - this}ms" } })
                { logger.error { it } }
        }
    }
}