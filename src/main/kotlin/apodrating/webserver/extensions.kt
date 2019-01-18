package apodrating.webserver

import apodrating.model.Error
import apodrating.model.toJsonString
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.core.net.PemKeyCertOptions
import io.vertx.reactivex.ext.web.RoutingContext
import org.apache.http.HttpStatus

/**
 * Validate the api and tell the router to route this context to the next matching route.
 */
fun handleApiKeyValidation(ctx: RoutingContext, apiKey: String) =
    when (ctx.request().getHeader("X-API-KEY")) {
        apiKey -> ctx.next()
        else -> ctx.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end(
            Error(
                HttpStatus.SC_UNAUTHORIZED,
                "Api key not valid"
            ).toJsonString()
        )
    }

/**
 * Check if the apodId is an integer
 */
fun checkApodIdValid(ctx: RoutingContext) =
    when {
        ctx.pathParam("apodId").toIntOrNull() != null -> ctx.next()
        else -> ctx.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).end(
            Error(
                HttpStatus.SC_BAD_REQUEST,
                "path parameter must be an integer, e.g. /apod/42"
            ).toJsonString()
        )
    }

/**
 * Check if the request contains a valid rating value.
 */
fun checkRatingValue(ctx: RoutingContext) = when (ctx.bodyAsJson.getInteger("rating", 0)) {
    in 1..10 -> ctx.next()
    else -> ctx.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).end(
        Error(
            HttpStatus.SC_BAD_REQUEST,
            "asRating must be an integer between 1 and 10"
        ).toJsonString()
    )
}

/**
 * Options necessary for creating an http2 server
 */
fun http2ServerOptions(): HttpServerOptions = HttpServerOptions()
    .setKeyCertOptions(PemKeyCertOptions().setCertPath("tls/server-cert.pem").setKeyPath("tls/server-key.pem"))
    .setSsl(true)
    .setUseAlpn(true)
