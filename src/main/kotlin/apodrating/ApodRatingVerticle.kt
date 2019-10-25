package apodrating

import apodrating.model.ApodRatingConfiguration
import apodrating.model.Error
import apodrating.model.toJsonString
import apodrating.remoteproxy.RemoteProxyService
import apodrating.remoteproxy.RemoteProxyServiceFactory
import apodrating.webapi.ApodQueryService
import apodrating.webapi.ApodQueryServiceFactory
import apodrating.webapi.RatingService
import apodrating.webapi.RatingServiceFactory
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.functions.Function4
import io.reactivex.rxkotlin.toCompletable
import io.reactivex.schedulers.Schedulers
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.net.pemKeyCertOptionsOf
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.core.http.HttpServerRequest
import io.vertx.reactivex.ext.jdbc.JDBCClient
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.reactivex.ext.web.handler.StaticHandler
import io.vertx.serviceproxy.ServiceBinder
import mu.KLogging
import org.apache.http.HttpStatus

class ApodRatingVerticle : AbstractVerticle() {

    companion object : KLogging()

    private lateinit var client: JDBCClient
    private lateinit var apodConfig: ApodRatingConfiguration

    override fun start(startFuture: Promise<Void>?) {
        apodConfig = ApodRatingConfiguration(config())
        client = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig())

        Single.zip(
            serviceBinderCompletable(vertx, config()).toSingleDefault(true).onErrorReturn { false },
            databaseObs(),
            http11Server(apodConfig),
            http2Server(apodConfig),
            Function4<Boolean, Int, HttpServer, HttpServer, Pair<Int, Int>> { service, db, s1, s2 ->
                Pair(s1.actualPort(), s2.actualPort())
            })
            .subscribeOn(Schedulers.io())
            .subscribe({
                logger.info { "server listens on ports ${it.first} and ${it.second}" }
                startFuture?.complete()
            }) {
                startFuture?.fail("could not start http2 server. ${it.message} ")
            }
    }

    private fun serviceBinderCompletable(vertx: Vertx, config: JsonObject): Completable = {
        with(ServiceBinder(vertx.delegate)) {
            setAddress(RATING_SERVICE_ADDRESS).register(
                RatingService::class.java,
                RatingServiceFactory.create(vertx.delegate, config)
            )
            setAddress(REMOTE_PROXY_SERVICE_ADDRESS)
                .register(
                    RemoteProxyService::class.java,
                    RemoteProxyServiceFactory.create(vertx.delegate, config)
                )
            setAddress(APOD_QUERY_ADDRESS).register(
                ApodQueryService::class.java,
                ApodQueryServiceFactory.create(vertx.delegate, config)
            )
        }
    }.toCompletable()

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
        OpenAPI3RouterFactory.rxCreate(vertx, "swagger.yaml")
            .map { it.mountServicesFromExtensions() }
            .map { routerFactory ->
                (httpServerOptions?.let { vertx.createHttpServer(it) }
                    ?: vertx.createHttpServer()).requestHandler(createRouter(routerFactory))
            }
            .flatMap { it.rxListen(port) }

    private fun createRouter(routerFactory: OpenAPI3RouterFactory): Handler<HttpServerRequest> =
        routerFactory.apply {
            addSecurityHandler(API_AUTH_KEY) { handleApiKeyValidation(it, apodConfig.nasaApiKey) }
        }.router.apply {
            route(STATIC_PATH).handler(StaticHandler.create())
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
