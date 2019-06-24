package apodrating.webapi

import io.vertx.codegen.annotations.ProxyClose
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.ext.web.api.generator.WebApiServiceGen

/**
 * Interface for the service for all APOD rating related queries.
 *
 * @see https://vertx.io/docs/vertx-web-api-service/java/
 */
@WebApiServiceGen
interface RatingService {

    /**
     * Get a rating for an apod
     */
    fun putRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    /**
     * Add a rating for an apod
     */
    fun getRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )

    /**
     *  Used to denote that a call to this function will close the remote connection to the event bus.
     */
    @ProxyClose
    fun close()
}