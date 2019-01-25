package apodrating.webserver

import apodrating.model.Error
import apodrating.model.asApodRequest
import apodrating.model.toJsonString
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.net.PemKeyCertOptions
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
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
        PemKeyCertOptions()
            .setCertPath("tls/server-cert.pem")
            .setKeyPath("tls/server-key.pem")
    )
    .setSsl(true)
    .setUseAlpn(true)

suspend fun prepareHandlePostApod(ctx: RoutingContext, client: JDBCClient) {
    val apodRequest = asApodRequest(ctx.bodyAsJson)
    val resultSet = client.queryWithParamsAwait("SELECT DATE_STRING FROM APOD WHERE DATE_STRING=?",
        json { array(apodRequest.dateString) })
    when {
        resultSet.rows.size == 0 -> ctx.next()
        else -> ctx.response().setStatusCode(HttpStatus.SC_CONFLICT).end(
            Error(
                HttpStatus.SC_CONFLICT,
                "Entry already exists"
            ).toJsonString()
        )
    }
}
