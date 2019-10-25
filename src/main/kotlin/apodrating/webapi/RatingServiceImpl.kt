package apodrating.webapi

import apodrating.model.ApodRatingConfiguration
import apodrating.model.Rating
import apodrating.model.asRatingRequest
import apodrating.model.toJsonObject
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.jdbc.JDBCClient
import mu.KLogging
import org.apache.http.HttpStatus

class RatingServiceImpl(
    val vertx: Vertx,
    val config: JsonObject,
    private val apodConfig: ApodRatingConfiguration = ApodRatingConfiguration(config),
    private val jdbc: JDBCClient = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig())
) : RatingService {

    companion object : KLogging()

    override fun getRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): RatingService {
        jdbc.rxQuerySingleWithParams(
            "SELECT APOD_ID, AVG(VALUE) AS VALUE FROM RATING WHERE APOD_ID=? GROUP BY APOD_ID",
            JsonArray().add(apodId)
        )
            .observeOn(Schedulers.computation())
            .map { Rating(it.getInteger(0), it.getInteger(1)) }
            .map { Future.succeededFuture(OperationResponse.completedWithJson(it.toJsonObject())) }
            .switchIfEmpty(handleApodNotFound())
            .subscribeOn(Schedulers.io())
            .subscribe(resultHandler::handle) { handleFailure(resultHandler, it) }
        return this
    }

    override fun putRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): RatingService {
        jdbc.rxQuerySingleWithParams("SELECT ID FROM APOD WHERE ID=?",
            json { array(apodId) }
        ).map { it.getInteger(0) }
            .flatMap {
                jdbc.rxUpdateWithParams(
                    "INSERT INTO RATING (VALUE, APOD_ID) VALUES ?, ?",
                    json { array(asRatingRequest(context.params.getJsonObject("body")).rating, apodId) }
                ).toMaybe()
            }
            .observeOn(Schedulers.computation())
            .map { succeed(HttpStatus.SC_NO_CONTENT) }
            .switchIfEmpty(handleApodNotFound())
            .subscribeOn(Schedulers.io())
            .subscribe(resultHandler::handle) { handleFailure(resultHandler, it) }
        return this
    }

    override fun close() {
        jdbc.close()
    }
}

