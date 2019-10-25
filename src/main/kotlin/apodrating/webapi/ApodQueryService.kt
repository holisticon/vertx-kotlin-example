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

@WebApiServiceGen
@ProxyGen
@VertxGen
interface ApodQueryService {

    @Fluent
    fun getApodForId(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

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

    @Fluent
    fun postApod(
        body: JsonObject,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService

    @ProxyClose
    fun close()
}

object ApodQueryServiceFactory {
    fun create(vertx: Vertx, config: JsonObject): ApodQueryService =
        ApodQueryServiceImpl(io.vertx.reactivex.core.Vertx(vertx), config)

    fun createProxy(vertx: Vertx, address: String) =
        apodrating.webapi.reactivex.ApodQueryService(ApodQueryServiceVertxEBProxy(vertx, address))
}