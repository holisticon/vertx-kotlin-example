package apodrating.webapi

import io.vertx.codegen.annotations.ProxyGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.ext.web.api.generator.WebApiServiceGen

/**
 * Interface for the service for all APOD  related queries.
 *
 * @see https://vertx.io/docs/vertx-web-api-service/java/
 */
@WebApiServiceGen
@ProxyGen
interface ApodQueryService {
    
    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    fun getApods(
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )
}