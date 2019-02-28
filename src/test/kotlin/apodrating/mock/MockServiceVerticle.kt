package apodrating.mock

import apodrating.mock.model.MockApod
import io.vertx.core.Future
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KLogging
import org.apache.http.HttpStatus

/**
 * Mock the NASA api to allow integration tests without accessing the real API.
 */
class MockServiceVerticle : CoroutineVerticle() {
    companion object : KLogging()

    private lateinit var rxVertx: Vertx

    override fun start(startFuture: Future<Void>?) {
        launch {
            rxVertx = Vertx(vertx)

            withContext(
                Dispatchers.IO, block = startHttpServer(
                    8091, startFuture
                )
            )
        }
        startFuture?.complete()
    }

    private fun startHttpServer(
        port: Int,
        startFuture: Future<Void>?
    ): suspend CoroutineScope.() -> Unit {
        return {
            OpenAPI3RouterFactory.create(rxVertx, "mock-api.yaml") {
                when (it.succeeded()) {
                    true -> with(it.result()) {
                        coroutineHandler(operationId = "getMockData") { handlePutApodRating(it) }
                        //coroutineSecurityHandler("ApiKeyAuth") { handleApiKeyValidation(it, "") }
                        rxVertx.createHttpServer()
                            .requestHandler(this.router)
                            .listen(port)
                    }
                    else -> startFuture?.fail("could not start http2 server.")
                }
            }
        }
    }

    private fun handlePutApodRating(ctx: RoutingContext) {
        val date = ctx.queryParam("date")[0]
        val apod = MockApod(
            title = "title",
            date = date,
            explanation = "expl",
            mediaType = "v1",
            serviceVersion = "v2",
            url = "uri",
            hdurl = "hduri"
        )
        ctx.response()
            .putHeader("content-type", "application/json")
            .setStatusCode(HttpStatus.SC_OK)
            .end(io.vertx.core.json.JsonObject.mapFrom(apod).encode())
    }

    private fun OpenAPI3RouterFactory.coroutineHandler(
        operationId: String,
        function: suspend (RoutingContext) -> Unit
    ) =
        addHandlerByOperationId(operationId) {
            launch { function(it) }
        }

    private fun OpenAPI3RouterFactory.coroutineSecurityHandler(
        securitySchemaName: String,
        function: suspend (RoutingContext) -> Unit
    ) =
        addSecurityHandler(securitySchemaName) {
            launch { function(it) }
        }

    private fun handleApiKeyValidation(ctx: RoutingContext, apiKey: String) = ctx.next()
}

