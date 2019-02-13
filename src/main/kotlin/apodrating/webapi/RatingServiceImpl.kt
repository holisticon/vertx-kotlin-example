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
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import io.vertx.kotlin.ext.sql.updateWithParamsAwait
import io.vertx.serviceproxy.ServiceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.apache.http.HttpStatus
import kotlin.coroutines.CoroutineContext

class RatingServiceImpl(val client: JDBCClient, override val coroutineContext: CoroutineContext) : RatingService,
    CoroutineScope {

    companion object : KLogging()

    override fun getRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) = runBlocking { getRating(apodId, resultHandler) }

    private suspend fun getRating(apodId: String, resultHandler: Handler<AsyncResult<OperationResponse>>) {
        coroutineScope {
            client.queryWithParams(
                "SELECT APOD_ID, AVG(VALUE) AS VALUE FROM RATING WHERE APOD_ID=? GROUP BY APOD_ID",
                JsonArray().add(apodId)
            ) {
                with(it.result()) {
                    resultHandler.handle(
                        when (this.rows.size) {
                            1 -> Future.succeededFuture(
                                OperationResponse.completedWithJson(asRating(this).toJsonObject())
                            )

                            0 -> Future.failedFuture(
                                ServiceException(
                                    HttpStatus.SC_NOT_FOUND,
                                    "A rating for this asApod entry does not exist"
                                )
                            )
                            else ->
                                Future.failedFuture(
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
    }

    override fun putRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ) {
        runBlocking {
            val rating = asRatingRequest(context.params)
            val result = this@RatingServiceImpl.client.queryWithParamsAwait(
                "SELECT ID FROM APOD WHERE ID=?",
                json { array(apodId) })
            when {
                result.rows.size == 1 -> {
                    client.updateWithParamsAwait(
                        "INSERT INTO RATING (VALUE, APOD_ID) VALUES ?, ?",
                        json { array(rating.rating, apodId) })
                    Future.succeededFuture(
                        with(OperationResponse()) {
                            this.setStatusCode(HttpStatus.SC_NO_CONTENT)
                        }
                    )
                }
                else -> resultHandler.handle(
                    Future.failedFuture(
                        ServiceException(
                            HttpStatus.SC_NOT_FOUND,
                            "apod entry does not exist"
                        )
                    )
                )
            }
        }
    }
}

