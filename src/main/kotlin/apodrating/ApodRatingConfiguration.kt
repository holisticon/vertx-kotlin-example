package apodrating

import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx

data class ApodRatingConfiguration(val config: JsonObject) {

    val port: Int = config.getInteger("APODRATING_PORT", 8080)
    val h2Port: Int = config.getInteger("APODRATING_H2_PORT", 8443)
    val jdbcUrl: String = config.getString("JDBC_URL", "org.hsqldb.jdbcDriver")
    val jdbcDriver: String = config.getString("JDBC_DRIVER", "org.hsqldb.jdbcDriver")
    val jdbcPoolSize: Int = config.getInteger("JDBC_MAX_POOL_SIZE", 30)
    var nasaApiKey: String = config.getString("NASA_API_KEY")

    fun toJsonObject() = json {
        obj(
            "url" to jdbcUrl,
            "driver_class" to jdbcDriver,
            "max_pool_size-loop" to jdbcPoolSize
        )
    }
}

/**
 * Convert ConfigRetrieverOptions into DeploymentOptions
 */
fun ConfigRetrieverOptions.deploymentOptions(vertx: Vertx): DeploymentOptions =
    DeploymentOptions(
        JsonObject().put(
            "config",
            ConfigRetriever.getConfigAsFuture(ConfigRetriever.create(vertx, this)).result()
        )
    )
