package apodrating.mock

import apodrating.mock.model.MockApod
import io.vertx.core.Promise
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

    override fun start(startFuture: Promise<Void>?) {
        launch {
            rxVertx = Vertx(vertx)
            withContext(
                Dispatchers.IO, rxStartHttpServer(config.getInteger("MOCKSERVER_PORT"), startFuture)
            )
        }
        startFuture?.complete()
    }

    private fun rxStartHttpServer(
        port: Int,
        startFuture: Promise<Void>?
    ): suspend CoroutineScope.() -> Unit = {
        OpenAPI3RouterFactory.rxCreate(rxVertx, "mock-api.yaml")
            .map {
                it.coroutineHandler(operationId = "getMockData") { handlePutApodRating(it) }
            }.map {
                rxVertx.createHttpServer()
                    .requestHandler(it.router)
                    .listen(port)
            }
            .subscribe({ logger.info { "started mockserver on port ${it.actualPort()}" } })
            { startFuture?.fail("could not start http2 server.") }

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
        addHandlerByOperationId(operationId) { launch { function(it) } }
}

