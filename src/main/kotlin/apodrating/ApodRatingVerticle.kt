package apodrating

import apodrating.model.Apod
import apodrating.model.ApodRatingConfiguration
import apodrating.model.Error
import apodrating.model.apodQueryParameters
import apodrating.model.asApod
import apodrating.model.asApodRequest
import apodrating.model.asRating
import apodrating.model.asRatingRequest
import apodrating.model.isEmpty
import apodrating.model.toJsonString
import apodrating.webapi.RatingService
import apodrating.webapi.RatingServiceImpl
import apodrating.webserver.handleApiKeyValidation
import apodrating.webserver.http2ServerOptions
import apodrating.webserver.prepareHandlePostApod
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.JsonArray
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.ext.sql.executeAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import io.vertx.kotlin.ext.sql.updateWithParamsAwait
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.core.http.HttpServerRequest
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import io.vertx.reactivex.ext.web.handler.StaticHandler
import io.vertx.serviceproxy.ServiceBinder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                "INSERT INTO RATING (VALUE, APOD_ID) VALUES 7, 2"

            )

            with(ServiceBinder(vertx).setAddress("rating_service.apod")) {
                this.register(
                    RatingService::class.java,
                    RatingServiceImpl(client, this@ApodRatingVerticle.coroutineContext)
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

    /**
     * Creates a router for our web servers.
     * @param routerFactory the router factory
     *
     */
    private fun createRouter(routerFactory: OpenAPI3RouterFactory): Handler<HttpServerRequest> =
        routerFactory.apply {
            //coroutineHandler(operationId = OPERATION_PUT_RATING) { handlePutApodRating(it) }

            //coroutineHandler(operationId = OPERATION_GET_RATING) { handleGetRating(it) }

            coroutineHandler(operationId = OPERATION_GET_APOD_FOR_DATE) { prepareHandleGetApodForDate(it) }
            coroutineHandler(operationId = OPERATION_GET_APOD_FOR_DATE) { handleGetApodForDate(it) }

            coroutineHandler(operationId = OPERATION_GET_APODS) { handleGetApods(it) }

            coroutineHandler(operationId = OPERATION_POST_APOD) { prepareHandlePostApod(it, client) }
            coroutineHandler(operationId = OPERATION_POST_APOD) { handlePostApod(it) }

            coroutineSecurityHandler(API_AUTH_KEY) { handleApiKeyValidation(it, apiKey) }
        }.router.apply {
            route(STATIC_PATH).handler(StaticHandler.create())
        }

    /**
     * Handle a POST request for a single APOD identified by a date string.
     *
     * @param ctx the vertx routing context
     */
    private suspend fun handlePostApod(ctx: RoutingContext) {
        val apodRequest = asApodRequest(ctx.bodyAsJson)
        val apiKeyHeader = ctx.request().getHeader(API_KEY_HEADER)
        val updateResult = client.updateWithParamsAwait(
            "INSERT INTO APOD (DATE_STRING) VALUES ?",
            json { array(apodRequest.dateString) })
        val newId = updateResult.keys.get<Int>(0)
        rxVertx.eventBus().rxSend<JsonObject>(
            EVENTBUS_ADDRESS,
            apodQueryParameters(newId.toString(), apodRequest.dateString, apiKeyHeader)
        ).map { asApod(it.body()) }
            .subscribe({
                ctx.response().setStatusCode(HttpStatus.SC_CREATED)
                    .putHeader(LOCATION_HEADER, "/apod/$newId")
                    .end()
            }) {
                ctx.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .end(
                        Error(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            "Could not create apod entry. ${it.message}"
                        ).toJsonString()
                    )
            }
    }

    /**
     * Handle a GET request for all APODs in our database.
     *
     * @param ctx the vertx routing context
     */
    private suspend fun handleGetApods(ctx: RoutingContext) {
        val apiKeyHeader = ctx.request().getHeader(API_KEY_HEADER)
        val result = client.queryAwait("SELECT ID, DATE_STRING FROM APOD ")
        var apods: List<JsonObject>? = null
        when {
            result.rows.size > 0 -> apods = result.rows
            result.rows.size == 0 -> ctx.response().setStatusCode(HttpStatus.SC_OK).end(JsonArray().encode())
            else -> ctx.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end()
        }
        apods?.apply {
            val singleApods = this
                .map {
                    runBlocking {
                        rxVertx.eventBus().rxSend<JsonObject>(
                            EVENTBUS_ADDRESS,
                            apodQueryParameters(
                                it.getInteger("ID").toString(),
                                it.getString("DATE_STRING"),
                                apiKeyHeader
                            )
                        ).map { msg -> asApod(msg.body()) }
                    }
                }
                .toList()
            Single.zip<Apod, List<Apod>>(singleApods) { emittedApodsAsJsonArray ->
                emittedApodsAsJsonArray
                    .filterIsInstance<Apod>()
                    .filter { !it.isEmpty() }
            }.subscribeOn(Schedulers.io())
                .subscribe({ ctx.response().setStatusCode(HttpStatus.SC_OK).end(Json.array(it).encode()) })
                {
                    ctx.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .end(Error(HttpStatus.SC_INTERNAL_SERVER_ERROR, "${it.message}").toJsonString())
                }
        }
    }

    /**
     * Check if the apod exists. If it does, store it in the context and let the next handler do the subsequent
     * processing.
     *
     * If it does not exists or the request is somewhat erroneous, end the request here with the proper status code.
     *
     * @param ctx the vertx routing context
     */
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

    /**
     * Handle a GET request for a single APOD identified by a date string.
     *
     * @param ctx the vertx routing context
     */
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

    /**
     * Serve a POST request. Rate an apod
     *
     * @param ctx the vertx RoutingContext
     */
    private suspend fun handlePutApodRating(ctx: RoutingContext) {
        val apodId = ctx.pathParam(PARAM_APOD_ID)
        val rating = asRatingRequest(ctx.bodyAsJson)
        val result = client.queryWithParamsAwait("SELECT ID FROM APOD WHERE ID=?", json { array(apodId) })
        when {
            result.rows.size == 1 -> {
                client.updateWithParamsAwait(
                    "INSERT INTO RATING (VALUE, APOD_ID) VALUES ?, ?",
                    json { array(rating.rating, apodId) })
                ctx.response().setStatusCode(HttpStatus.SC_NO_CONTENT).end()
            }
            else -> ctx.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end(
                Error(
                    HttpStatus.SC_NOT_FOUND,
                    "Apod does not exist."
                ).toJsonString()
            )
        }
    }

    /**
     * Serve a GET request. Get the current rating of an apod
     *
     * @param ctx the vertx RoutingContext
     */
    private suspend fun handleGetRating(ctx: RoutingContext) {
        val apodId = ctx.pathParam(PARAM_APOD_ID)
        val result = client.queryWithParamsAwait(
            "SELECT APOD_ID, AVG(VALUE) AS VALUE FROM RATING WHERE APOD_ID=? GROUP BY APOD_ID",
            json { array(apodId) })
        when (result.rows.size) {
            1 -> ctx.response().end(asRating(result).toJsonString())
            0 -> ctx.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end(
                Error(
                    HttpStatus.SC_NOT_FOUND,
                    "A rating for this asApod entry does not exist"
                ).toJsonString()
            )
            else -> ctx.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end(
                Error(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Server error"
                ).toJsonString()
            )
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
