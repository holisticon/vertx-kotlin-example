package apodrating.mock

import apodrating.mock.model.MockApod
import io.vertx.core.Promise
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import org.apache.logging.log4j.kotlin.Logging
import org.apache.http.HttpStatus

/**
 * Mock the NASA api to allow integration tests without accessing the real API.
 */
class MockServiceVerticle : AbstractVerticle() {
    companion object : Logging

    override fun start(startFuture: Promise<Void>?) {
        OpenAPI3RouterFactory.rxCreate(vertx, "mock-api.yaml")
            .map {
                it.addHandlerByOperationId("getMockData") { handlePutApodRating(it) }
            }.map {
                vertx.createHttpServer()
                    .requestHandler(it.router)
                    .listen(config().getInteger("MOCKSERVER_PORT"))
            }
            .subscribe({ logger.info { "started mockserver on port ${it.actualPort()}" } })
            { startFuture?.fail("could not start http2 server.") }
        startFuture?.complete()
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
}

