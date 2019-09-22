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
import io.reactivex.Single
import io.reactivex.functions.Function3
import io.reactivex.rxkotlin.toCompletable
import io.reactivex.schedulers.Schedulers
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.core.net.pemKeyCertOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.core.http.HttpServerRequest
import io.vertx.reactivex.ext.jdbc.JDBCClient
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.reactivex.ext.web.handler.StaticHandler
import io.vertx.serviceproxy.ServiceBinder
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
    override fun start(startFuture: Promise<Void>?) {
        val apodConfig = ApodRatingConfiguration(config)
        rxVertx = Vertx(vertx)
        client = JDBCClient.createShared(rxVertx, apodConfig.toJdbcConfig())
        apiKey = apodConfig.nasaApiKey

        val serviceBinderCpl = {
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
        }.toCompletable().
            .subscribe()

        Single.zip(
            databaseObs(),
            http11Server(apodConfig),
            http2Server(apodConfig),
            Function3<Int, HttpServer, HttpServer, Pair<Int, Int>> { db, s1, s2 ->
                Pair(s1.actualPort(), s2.actualPort())
            })
            .subscribeOn(Schedulers.io())
            .subscribe({
                logger.info { "server listens on ports ${it.first} and ${it.second}" }
                startFuture?.complete()
            }) {
                startFuture?.fail("could not start http2 server.")
            }
    }

    private fun http2Server(apodConfig: ApodRatingConfiguration): Single<HttpServer> = startHttpServer(
        apodConfig.h2Port,
        HttpServerOptions()
            .setKeyCertOptions(
                pemKeyCertOptionsOf(certPath = "tls/server-cert.pem", keyPath = "tls/server-key.pem")
            )
            .setSsl(true)
            .setUseAlpn(true)
    )

    private fun http11Server(apodConfig: ApodRatingConfiguration): Single<HttpServer> = startHttpServer(apodConfig.port)

    private fun databaseObs(): Single<Int> = client
        .rxGetConnection()
        .flatMap { it.rxBatch(STATEMENTS) }
        .map { it.size }
        .subscribeOn(Schedulers.computation())

    private fun startHttpServer(
        port: Int,
        httpServerOptions: HttpServerOptions? = null
    ): Single<HttpServer> =
        OpenAPI3RouterFactory.rxCreate(rxVertx, "swagger.yaml")
            .map { it.mountServicesFromExtensions() }
            .map { routerFactory ->
                (httpServerOptions?.let { rxVertx.createHttpServer(it) }
                    ?: rxVertx.createHttpServer()).requestHandler(createRouter(routerFactory))
            }
            .flatMap { it.rxListen(port) }

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
