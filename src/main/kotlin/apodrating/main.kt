package apodrating

import apodrating.model.deploymentOptionsFromEnv
import io.vertx.reactivex.core.Vertx

fun main() = with(Vertx.vertx()) {
    deploymentOptionsFromEnv(this)
        .flatMap {
            this.rxDeployVerticle(MainVerticle::class.java.canonicalName, it)
        }.subscribe()
        .dispose()
}



