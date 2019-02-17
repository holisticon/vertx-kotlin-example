package apodrating

import apodrating.model.ApodRatingConfiguration
import apodrating.model.apodQueryParameters
import apodrating.model.asApod
import apodrating.model.isEmpty
import apodrating.model.toJsonString
import apodrating.webapi.ApodQueryService
import apodrating.webapi.ApodQueryServiceImpl
import apodrating.webapi.RatingService
import apodrating.webapi.RatingServiceImpl
import apodrating.webserver.handleApiKeyValidation
import apodrating.webserver.http2ServerOptions
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.ext.sql.executeAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.core.http.HttpServerRequest
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
    override fun start(startFuture: Future<Void>?) {
        launch {
            val apodConfig = ApodRatingConfiguration(config)
            client = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig())
            apiKey = apodConfig.nasaApiKey
            rxVertx = Vertx(vertx)
            val statements = listOf(
                "CREATE TABLE APOD (ID INTEGER IDENTITY PRIMARY KEY, DATE_STRING VARCHAR(16))",
                "CREATE TABLE RATING (ID INTEGER IDENTITY PRIMARY KEY, VALUE INTEGER, APOD_ID INTEGER, FOREIGN KEY (APOD_ID) REFERENCES APOD(ID))",
                "INSERT INTO APOD (DATE_STRING) VALUES '2019-01-10'",
                "INSERT INTO APOD (DATE_STRING) VALUES '2018-07-01'",
                "INSERT INTO APOD (DATE_STRING) VALUES '2017-01-01'",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 8, 0",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 5, 1",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 7, 2",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 8, 0",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 5, 1",
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 7, 2",
                "ALTER TABLE APOD ADD CONSTRAINT APOD_UNIQUE UNIQUE(DATE_STRING)"
            )
            with(ServiceBinder(vertx)) {
                this.setAddress("rating_service.apod").register(
                    RatingService::class.java,
                    RatingServiceImpl(rxVertx, config)
                )
                this.setAddress("apod_query_service.apod").register(
                    ApodQueryService::class.java,
                    ApodQueryServiceImpl(rxVertx, config)
                )
            }


            client.getConnectionAwait().use { connection -> statements.forEach { connection.executeAwait(it) } }
            val http11Server =
                OpenAPI3RouterFactory.rxCreate(rxVertx, "swagger.yaml").map {
                    it.mountServicesFromExtensions()
                    rxVertx.createHttpServer()
                        .requestHandler(createRouter(it))
                        .listen(apodConfig.port)
                }
            val http2Server =
                OpenAPI3RouterFactory.rxCreate(rxVertx, "swagger.yaml").map {
                    it.mountServicesFromExtensions()
                    rxVertx.createHttpServer(http2ServerOptions())
                        .requestHandler(createRouter(it))
                        .listen(apodConfig.h2Port)
                }
            Single.zip(listOf(http11Server, http2Server)) {
                it
                    .filterIsInstance<HttpServer>()
                    .map { eachHttpServer ->
                        logger.info { "port: ${eachHttpServer.actualPort()}" }
                        eachHttpServer.actualPort()
                    }
            }.doOnSuccess { startFuture?.complete() }
                .subscribeOn(Schedulers.io())
                .subscribe({ logger.info { "started ${it.size} servers" } })
                { logger.error { it } }
        }
    }

    private fun createRouter(routerFactory: OpenAPI3RouterFactory): Handler<HttpServerRequest> =
        routerFactory.apply {
            coroutineHandler(operationId = OPERATION_GET_APOD_FOR_DATE) { prepareHandleGetApodForDate(it) }
            coroutineHandler(operationId = OPERATION_GET_APOD_FOR_DATE) { handleGetApodForDate(it) }

            coroutineSecurityHandler(API_AUTH_KEY) { handleApiKeyValidation(it, apiKey) }
        }.router.apply {
            route(STATIC_PATH).handler(StaticHandler.create())
        }

    private suspend fun prepareHandleGetApodForDate(ctx: RoutingContext) {
        val apodId = ctx.pathParam(PARAM_APOD_ID)
        val result =
            client.queryWithParamsAwait("SELECT ID, DATE_STRING FROM APOD WHERE ID=?", json { array(apodId) })
        when (result.rows.size) {
            1 -> ctx.put(CTX_FIELD_APOD, result.rows[0]).next()
            0 -> ctx.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end()
            else -> ctx.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).end()
        }
    }

    private fun handleGetApodForDate(ctx: RoutingContext) {
        val jsonObject = ctx.get<JsonObject?>(CTX_FIELD_APOD)
        jsonObject?.apply {
            rxVertx.eventBus().rxSend<JsonObject>(
                EVENTBUS_ADDRESS,
                apodQueryParameters(
                    this.getInteger("ID").toString(),
                    this.getString("DATE_STRING"),
                    ctx.request().getHeader(API_KEY_HEADER)
                )
            ).map { asApod(it.body()) }
                .subscribe({
                    when {
                        it == null || it.isEmpty() -> ctx.response().setStatusCode(HttpStatus.SC_SERVICE_UNAVAILABLE).end()
                        else -> ctx.response().setStatusCode(HttpStatus.SC_OK).end(it.toJsonString())
                    }
                }) {
                    logger.error { it }
                    ctx.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end()
                }
        }
    }

    private fun OpenAPI3RouterFactory.coroutineHandler(
        operationId: String,
        function: suspend (RoutingContext) -> Unit
    ) =
        addHandlerByOperationId(operationId) {
            launch { function(it) }
        }

    private fun OpenAPI3RouterFactory.coroutineSecurityHandler(
        securitySchemaName: String,
        function: suspend (RoutingContext) -> Unit
    ) =
        addSecurityHandler(securitySchemaName) {
            launch { function(it) }
        }
}
