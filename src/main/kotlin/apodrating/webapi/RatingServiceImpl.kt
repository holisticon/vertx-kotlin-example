package apodrating.webapi

import apodrating.model.asRating
import apodrating.model.asRatingRequest
import apodrating.model.toJsonObject
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.kotlin.core.json.JsonArray
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.serviceproxy.ServiceException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.apache.http.HttpStatus

class RatingServiceImpl(val client: JDBCClient) : RatingService {

    companion object : KLogging()

    override fun getRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) = runBlocking { getRatingSuspend(apodId, resultHandler) }

    private suspend fun getRatingSuspend(apodId: String, resultHandler: Handler<AsyncResult<OperationResponse>>) =
        coroutineScope<Unit> {
            client.queryWithParams(
                "SELECT APOD_ID, AVG(VALUE) AS VALUE FROM RATING WHERE APOD_ID=? GROUP BY APOD_ID",
                JsonArray().add(apodId)
            ) {
                with(it.result()) {
                    resultHandler.handle(
                        when (this.rows.size) {
                            1 -> Future.succeededFuture(OperationResponse.completedWithJson(asRating(this).toJsonObject()))
                            0 -> Future.succeededFuture(OperationResponse().setStatusCode(404).setStatusMessage("This apod does not exist."))
                            else -> Future.failedFuture(
                                ServiceException(
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    "Server error"
                                )
                            )
                        }
                    )
                }
            }
        }

    override fun putRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) {
        runBlocking { putRatingSuspending(apodId, context, resultHandler) }
    }

    private suspend fun putRatingSuspending(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) =
        coroutineScope<Unit> {
            client.queryWithParams(
                "SELECT ID FROM APOD WHERE ID=?",
                json { array(apodId) }) {
                with(it.result()) {
                    resultHandler.handle(
                        when (this.rows.size) {
                            1 -> client.updateWithParams(
                                "INSERT INTO RATING (VALUE, APOD_ID) VALUES ?, ?",
                                json { array(asRatingRequest(context.params.getJsonObject("body")).rating, apodId) }
                            ) {
                            }.let {
                                Future.succeededFuture(
                                    OperationResponse().setStatusCode(HttpStatus.SC_NO_CONTENT)
                                )
                            }
                            else -> Future.succeededFuture(OperationResponse().setStatusCode(404).setStatusMessage("This apod does not exist."))
                        }
                    )
                }
            }

        }
}

