package apodrating.webapi

import apodrating.API_KEY_HEADER
import apodrating.EVENTBUS_ADDRESS
import apodrating.LOCATION_HEADER
import apodrating.model.Apod
import apodrating.model.ApodRatingConfiguration
import apodrating.model.apodQueryParameters
import apodrating.model.asApod
import apodrating.model.asApodRequest
import apodrating.model.isEmpty
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.jdbc.JDBCClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.http.HttpStatus
import java.sql.SQLIntegrityConstraintViolationException

/**
 * Implementation of all APOD related queries.
 *
 * @see https://vertx.io/docs/vertx-web-api-service/java/
 */
class ApodQueryServiceImpl(
    val vertx: Vertx,
    val config: JsonObject,
    private val apodConfig: ApodRatingConfiguration = ApodRatingConfiguration(config),
    private val jdbc: JDBCClient = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig())
) : ApodQueryService {

    /**
     * Create a new apod in our database and return the resource's location in a http header.
     */
    override fun postApod(
        body: JsonObject,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) = runBlocking<Unit> {
        val apodRequest = asApodRequest(body)
        val apiKeyHeader = context.headers[API_KEY_HEADER]
        withContext(Dispatchers.IO) {
            jdbc.rxUpdateWithParams("INSERT INTO APOD (DATE_STRING) VALUES ?",
                json { array(apodRequest.dateString) })
        }.map { it.keys.get<Int>(0) }
            .flatMap {
                vertx.eventBus().rxSend<JsonObject>(
                    EVENTBUS_ADDRESS,
                    apodQueryParameters(it.toString(), apodRequest.dateString, apiKeyHeader)
                )
            }
            .map { asApod(it.body()) }
            .map {
                succeed(HttpStatus.SC_CREATED, LOCATION_HEADER, "/apod/${it.id}")
            }
            .onErrorReturn {
                when (it) {
                    is SQLIntegrityConstraintViolationException -> succeed(HttpStatus.SC_CONFLICT)
                    else -> fail(HttpStatus.SC_CONFLICT, it.localizedMessage)
                }
            }
            .subscribeOn(Schedulers.io())
            .subscribe(resultHandler::handle) {
                handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
            }
    }

    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    override fun getApods(context: OperationRequest, resultHandler: Handler<AsyncResult<OperationResponse>>) =
        runBlocking<Unit> {
            withContext(Dispatchers.IO) { jdbc.rxQuery("SELECT ID, DATE_STRING FROM APOD ") }
                .map { it.rows.toList() }
                .filter { it.isEmpty().not() }
                .map {
                    it.map {
                        runBlocking {
                            vertx.eventBus().rxSend<JsonObject>(
                                EVENTBUS_ADDRESS,
                                apodQueryParameters(
                                    it.getInteger("ID").toString(),
                                    it.getString("DATE_STRING"),
                                    context.headers[API_KEY_HEADER]
                                )
                            ).map { msg -> asApod(msg.body()) }
                        }
                    }.toList()
                }
                .flatMap {
                    Single.zip<Apod, List<Apod>>(it) { apodArray ->
                        apodArray
                            .filterIsInstance<Apod>()
                            .filter { apod -> !apod.isEmpty() }
                    }.toMaybe()
                }
                .map { Future.succeededFuture(OperationResponse.completedWithJson(Json.array(it))) }
                .switchIfEmpty(Maybe.just(Future.succeededFuture(OperationResponse.completedWithJson(JsonArray()))))
                .subscribeOn(Schedulers.io())
                .subscribe(resultHandler::handle) {
                    handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
                }
        }

    private fun handleFailure(
        resultHandler: Handler<AsyncResult<OperationResponse>>,
        it: Throwable,
        errorCode: Int
    ) {
        resultHandler.handle(fail(errorCode, it.localizedMessage))
    }
}

