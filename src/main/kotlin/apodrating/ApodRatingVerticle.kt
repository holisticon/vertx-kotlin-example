package apodrating

import apodrating.model.ApodRatingConfiguration
import apodrating.model.Error
import apodrating.model.toJsonString
import apodrating.remoteproxy.RemoteProxyService
import apodrating.remoteproxy.RemoteProxyServiceImpl
import apodrating.webapi.ApodQueryService
import apodrating.webapi.ApodQueryServiceImpl
import apodrating.webapi.RatingService
import apodrating.webapi.RatingServiceImpl
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.net.pemKeyCertOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.ext.sql.executeAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServerRequest
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.reactivex.ext.web.handler.StaticHandler
import io.vertx.serviceproxy.ServiceBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mu.KLogging
import org.apache.http.HttpStatus

/**
 * Implements our REST interface
 */
class ApodRatingVerticle : CoroutineVerticle() {

    companion object : KLogging()

    private lateinit var client: JDBCClient
    private lateinit var apiKey: String
    private lateinit var rxVertx: Vertx

    /**
     * - Start the verticle.
     * - Initialize Database
     * - Initialize vertx router
     * - Initialize webserver
     */
    override fun start(startFuture: Future<Void>?) {
        launch {
            val apodConfig = ApodRatingConfiguration(config)
            val jdbcRoutine = async {
                client = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig())
            }
            apiKey = apodConfig.nasaApiKey
            rxVertx = Vertx(vertx)
            val statements = listOf(
                "CREATE TABLE APOD (ID INTEGER IDENTITY PRIMARY KEY, DATE_STRING VARCHAR(16))",
                "CREATE TABLE RATING (ID INTEGER IDENTITY PRIMARY KEY, VALUE INTEGER, APOD_ID INTEGER, FOREIGN KEY (APOD_ID) REFERENCES APOD(ID))",
                "INSERT INTO APOD (DATE_STRING) VALUES '2019-01-10'",
                "INSERT INTO APOD (DATE_STRING) VALUES '2018-07-01'",
                "INSERT INTO APOD (DATE_STRING) VALUES '2017-01-01'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2016-01-01'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2015-01-01'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2019-02-01'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2019-02-02'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2019-02-03'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2017-07-01'",
//                "INSERT INTO APOD (DATE_STRING) VALUES '2012-06-12'",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 8, 0",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 5, 1",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 7, 2",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 8, 0",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 5, 1",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 7, 2",
                "ALTER TABLE APOD ADD CONSTRAINT APOD_UNIQUE UNIQUE(DATE_STRING)"
            )
            val serviceRoutine = async {
                with(ServiceBinder(vertx)) {
                    this.setAddress(RATING_SERVICE_ADDRESS).register(
                        RatingService::class.java,
                        RatingServiceImpl(rxVertx, config)
                    )
                    this.setAddress(REMOTE_PROXY_SERVICE_ADDRESS)
                        .register(RemoteProxyService::class.java, RemoteProxyServiceImpl(rxVertx, config))
                    this.setAddress(APOD_QUERY_ADDRESS).register(
                        ApodQueryService::class.java,
                        ApodQueryServiceImpl(rxVertx, config)
                    )
                }
            }

            val dbRoutine = async(Dispatchers.IO) {
                jdbcRoutine.await()
                client.getConnectionAwait().use { connection ->
                    statements.forEach {
                        connection.executeAwait(it)
                    }
                }
            }

            val http11ServerRoutine = async(
                Dispatchers.IO, block = startHttpServer(
                    apodConfig.port, startFuture
                )
            )
            val http2ServerRoutine = async(
                Dispatchers.IO,
                block = startHttpServer(
                    apodConfig.h2Port,
                    startFuture,
                    HttpServerOptions()
                        .setKeyCertOptions(
                            pemKeyCertOptionsOf(certPath = "tls/server-cert.pem", keyPath = "tls/server-key.pem")
                        )
                        .setSsl(true)
                        .setUseAlpn(true)
                )
            )

            serviceRoutine.await()
            dbRoutine.await()
            http11ServerRoutine.await()
            http2ServerRoutine.await()
            startFuture?.complete()
        }
    }

    private fun startHttpServer(
        port: Int,
        startFuture: Future<Void>?,
        httpServerOptions: HttpServerOptions? = null
    ): suspend CoroutineScope.() -> Unit = {
        OpenAPI3RouterFactory.rxCreate(rxVertx, "swagger.yaml")
            .map { it.mountServicesFromExtensions() }
            .map {
                (httpServerOptions?.let { rxVertx.createHttpServer(it) }
                    ?: rxVertx.createHttpServer()).requestHandler(createRouter(it))
            }
            .flatMap { it.rxListen(port) }
            .subscribe({ logger.info { "server listens on port ${it.actualPort()}" } }) {
                startFuture?.fail("could not start http2 server.")
            }
    }

    private fun createRouter(routerFactory: OpenAPI3RouterFactory): Handler<HttpServerRequest> =
        routerFactory.apply {
            coroutineSecurityHandler(API_AUTH_KEY) { handleApiKeyValidation(it, apiKey) }
        }.router.apply {
            route(STATIC_PATH).handler(StaticHandler.create())
        }

    private fun OpenAPI3RouterFactory.coroutineSecurityHandler(
        securitySchemaName: String,
        function: suspend (RoutingContext) -> Unit
    ) =
        addSecurityHandler(securitySchemaName) {
            launch { function(it) }
        }

    private fun handleApiKeyValidation(ctx: RoutingContext, apiKey: String) =
        when (ctx.request().getHeader("X-API-KEY")) {
            apiKey -> ctx.next()
            else -> ctx.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end(
                Error(
                    HttpStatus.SC_UNAUTHORIZED,
                    "Api key not valid"
                ).toJsonString()
            )
        }
}
