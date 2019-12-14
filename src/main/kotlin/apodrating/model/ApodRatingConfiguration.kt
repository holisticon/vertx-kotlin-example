package apodrating.model

import io.reactivex.Single
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx

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
        val cacheSize: Int = config.getInteger("CACHE_POOL_SIZE_ENTRIES"),
        val cacheLifetimeMinutes: Long = config.getLong("CACHE_LIFETIME_MINUTES"),
        val psqlHost: String = config.getString("POSTGRES_HOST"),
        val psqlPort: Int = config.getInteger("POSTGRES_PORT"),
        val psqlUser: String = config.getString("POSTGRES_USER"),
        val psqlPassword: String = config.getString("POSTGRES_PASSWORD"),
        val psqlDb: String = config.getString("POSTGRES_DB")

) {
    fun toJdbcConfig() = json {
        obj(
                "url" to jdbcUrl,
                "driver_class" to jdbcDriver,
                "max_pool_size-loop" to jdbcPoolSize
        )
    }

    fun toPsqlConfig() = json {
        obj(
                "host" to psqlHost,
                "port" to psqlPort,
                "username" to psqlUser,
                "password" to psqlPassword,
                "database" to psqlDb
        )
    }
}

fun ConfigRetrieverOptions.deploymentOptions(vertx: Vertx): Single<DeploymentOptions> =
        ConfigRetriever.create(vertx, this).rxGetConfig()
                .map { JsonObject().put("config", it) }
                .map { DeploymentOptions(it) }


