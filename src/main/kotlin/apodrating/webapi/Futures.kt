package apodrating.webapi

import io.reactivex.Maybe
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationResponse
import io.vertx.serviceproxy.ServiceException
import org.apache.http.HttpStatus

fun succeed(statusCode: Int, jsonObject: JsonObject? = null): Future<OperationResponse> = Future.succeededFuture(
    with(OperationResponse()) {
        this.payload = jsonObject?.toBuffer()
        this.statusCode = statusCode
        this
    }
)

fun succeed(statusCode: Int, headerName: String, headerValue: String): Future<OperationResponse> =
    Future.succeededFuture(
        OperationResponse().putHeader(headerName, headerValue).setStatusCode(statusCode)
    )

fun <T> fail(statusCode: Int, message: String): Future<T> = Future.failedFuture(
    ServiceException(
        statusCode,
        message
    )
)

fun <T> handleFailure(
    resultHandler: Handler<AsyncResult<T>>,
    it: Throwable,
    errorCode: Int = HttpStatus.SC_INTERNAL_SERVER_ERROR
) {
    resultHandler.handle(fail(errorCode, it.localizedMessage))
}

fun handleApodNotFound(): Maybe<Future<OperationResponse>>? {
    return Maybe.just(succeed(HttpStatus.SC_NOT_FOUND))
}