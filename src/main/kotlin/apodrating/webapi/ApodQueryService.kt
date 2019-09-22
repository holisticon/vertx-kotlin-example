package apodrating.webapi

import io.vertx.codegen.annotations.ProxyClose
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.ext.web.api.generator.WebApiServiceGen

/**
 * Interface for the service for all APOD  related queries.
 *
 * @see https://vertx.io/docs/vertx-web-api-service/java/
 */
@WebApiServiceGen
interface ApodQueryService {

    /**
     * Handle a GET request for a single APOD in our database.
     *
     */
    fun getApodForId(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    fun getApods(
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    fun getApodTitle(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    /**
     * Handle a POST request for all APODs in our database.
     *
     */
    fun postApod(
        body: JsonObject,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    /**
     *  Used to denote that a call to this function will close the remote connection to the event bus.
     */
    @ProxyClose
    fun close()
}