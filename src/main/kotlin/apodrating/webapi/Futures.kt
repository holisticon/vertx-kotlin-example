package apodrating.webapi

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationResponse
import io.vertx.serviceproxy.ServiceException

/**
 * Create a succeeding Future with a status code and an optional json payload
 */
fun succeed(statusCode: Int, jsonObject: JsonObject? = null): Future<OperationResponse> = Future.succeededFuture(
    with(OperationResponse()) {
        this.payload = jsonObject?.toBuffer()
        this.statusCode = statusCode
        this
    }
)

/**
 * Create a succeeding Future with a status code and an http header
 */
fun succeed(statusCode: Int, headerName: String, headerValue: String): Future<OperationResponse> =
    Future.succeededFuture(
        OperationResponse().putHeader(headerName, headerValue).setStatusCode(statusCode)
    )

/**
 * Create a failing Future with a status code and a status message
 */
fun fail(statusCode: Int, message: String): Future<OperationResponse> = Future.failedFuture(
    ServiceException(
        statusCode,
        message
    )
)