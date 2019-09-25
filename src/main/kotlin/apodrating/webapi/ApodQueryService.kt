package apodrating.webapi

import io.vertx.codegen.annotations.Fluent
import io.vertx.codegen.annotations.ProxyClose
import io.vertx.codegen.annotations.ProxyGen
import io.vertx.codegen.annotations.VertxGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
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
@ProxyGen
@VertxGen
interface ApodQueryService {

    /**
     * Handle a GET request for a single APOD in our database.
     *
     */
    @Fluent
    fun getApodForId(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    @Fluent
    fun getApods(
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

    @Fluent
    fun getApodTitle(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

    /**
     * Handle a POST request for all APODs in our database.
     *
     */
    @Fluent
    fun postApod(
        body: JsonObject,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

    /**
     *  Used to denote that a call to this function will close the remote connection to the event bus.
     */
    @ProxyClose
    fun close()
}

object ApodQueryServiceFactory {
    fun create(vertx: Vertx, config: JsonObject): ApodQueryService =
        ApodQueryServiceImpl(io.vertx.reactivex.core.Vertx(vertx), config)

    fun createProxy(vertx: Vertx, address: String) =
        apodrating.webapi.reactivex.ApodQueryService(ApodQueryServiceVertxEBProxy(vertx, address))
}