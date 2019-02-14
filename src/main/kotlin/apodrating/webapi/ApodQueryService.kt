package apodrating.webapi

import io.vertx.codegen.annotations.ProxyGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.ext.web.api.generator.WebApiServiceGen

@WebApiServiceGen
@ProxyGen
interface ApodQueryService {
    fun getApods(
        context: OperationRequest,
        resultHandler: Handler<AsyncResult<OperationResponse>>
    )
}