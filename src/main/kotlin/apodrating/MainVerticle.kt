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
        val players = listOf("1", "2", "3")
        val map = players.map {
            if (it == "3") "4" else  it
        }
        with(System.currentTimeMillis()) {
            vertx.rxDeployVerticle(verticleName, deploymentOptionsFromEnv(vertx))
                .subscribe({ logger.info { "Startup time ${System.currentTimeMillis() - this}ms" } })
                { logger.error { it } }
        }
    }
}