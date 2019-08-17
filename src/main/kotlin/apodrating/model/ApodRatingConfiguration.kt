package apodrating.model

import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx

/**
 * Holds the configurtion of our application.
 */
data class ApodRatingConfiguration(
    val config: JsonObject,
    val port: Int = config.getInteger("APODRATING_PORT"),
    val h2Port: Int = config.getInteger("APODRATING_H2_PORT"),
    val jdbcUrl: String = config.getString("JDBC_URL"),
    val jdbcDriver: String = config.getString("JDBC_DRIVER"),
    val jdbcPoolSize: Int = config.getInteger("JDBC_MAX_POOL_SIZE"),
    val nasaApiKey: String = config.getString("NASA_API_KEY"),
    val nasaApiHost: String = config.getString("NASA_API_HOST"),
    val nasaApiPath: String = config.getString("NASA_API_PATH"),
    val cacheSize: Long = config.getLong("CACHE_POOL_SIZE_ENTRIES")

) {

    /**
     * Return a JSON representation of this config object.
     */
    fun toJdbcConfig() = json {
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
