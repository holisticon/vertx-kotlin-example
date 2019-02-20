package apodrating.remoteproxy

import io.vertx.codegen.annotations.Fluent
import io.vertx.codegen.annotations.ProxyClose
import io.vertx.codegen.annotations.ProxyGen
import io.vertx.codegen.annotations.VertxGen
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject

@ProxyGen
@VertxGen
interface RemoteProxyService {

    @Fluent
    fun performApodQuery(
        id: String, date: String, nasaApiKey: String,
        resultHandler: Handler<AsyncResult<JsonObject>>
    ): RemoteProxyService

    @ProxyClose
    fun close()
}