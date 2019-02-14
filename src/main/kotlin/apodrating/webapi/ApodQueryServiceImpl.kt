package apodrating.webapi

import apodrating.API_KEY_HEADER
import apodrating.EVENTBUS_ADDRESS
import apodrating.model.Apod
import apodrating.model.apodQueryParameters
import apodrating.model.asApod
import apodrating.model.isEmpty
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.reactivex.core.Vertx
import io.vertx.serviceproxy.ServiceException
import kotlinx.coroutines.runBlocking
import org.apache.http.HttpStatus

class ApodQueryServiceImpl(
    val vertx: Vertx,
    val client: JDBCClient,
    private val jdbc: io.vertx.reactivex.ext.jdbc.JDBCClient = io.vertx.reactivex.ext.jdbc.JDBCClient(client)
) : ApodQueryService {

    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    override fun getApods(context: OperationRequest, resultHandler: Handler<AsyncResult<OperationResponse>>) {
        jdbc.rxQuery("SELECT ID, DATE_STRING FROM APOD ")
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
            .subscribe(resultHandler::handle) { handleFailure(resultHandler, it) }
    }

    private fun handleFailure(
        resultHandler: Handler<AsyncResult<OperationResponse>>,
        it: Throwable
    ) {
        resultHandler.handle(
            Future.failedFuture(
                ServiceException(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    it.localizedMessage
                )
            )
        )
    }
}

