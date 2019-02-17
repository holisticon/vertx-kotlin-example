package apodrating.webserver

import apodrating.model.Error
import apodrating.model.toJsonString
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.core.net.pemKeyCertOptionsOf
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
 * Options necessary for creating an http2 server
 */
fun http2ServerOptions(): HttpServerOptions = HttpServerOptions()
    .setKeyCertOptions(
        pemKeyCertOptionsOf(certPath = "tls/server-cert.pem", keyPath = "tls/server-key.pem")
    )
    .setSsl(true)
    .setUseAlpn(true)

