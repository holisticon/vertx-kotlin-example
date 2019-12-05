package apodrating

import apodrating.model.deploymentOptionsFromEnv
import io.vertx.reactivex.core.AbstractVerticle
import org.apache.logging.log4j.kotlin.Logging

private val verticleName: String = ApodRatingVerticle::class.java.canonicalName

class MainVerticle : AbstractVerticle() {
    companion object : Logging

    override fun start() {
        with(System.currentTimeMillis()) {
            deploymentOptionsFromEnv(vertx)
                    .flatMap { vertx.rxDeployVerticle(verticleName, it) }
                    .subscribe({ logger.debug("Startup time ${System.currentTimeMillis() - this}ms") })
                    { logger.error { it } }
        }
    }
}