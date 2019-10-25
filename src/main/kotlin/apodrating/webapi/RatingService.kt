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
interface RatingService {

    @Fluent
    fun putRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): RatingService

    @Fluent
    fun getRating(
        apodId: String,
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    ): RatingService

    @ProxyClose
    fun close()
}

object RatingServiceFactory {
    fun create(vertx: Vertx, config: JsonObject): RatingService =
        RatingServiceImpl(io.vertx.reactivex.core.Vertx(vertx), config)

    fun createProxy(vertx: Vertx, address: String) =
        apodrating.webapi.reactivex.RatingService(RatingServiceVertxEBProxy(vertx, address))
}