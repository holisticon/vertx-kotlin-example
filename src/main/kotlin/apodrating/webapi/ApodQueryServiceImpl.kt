package apodrating.webapi

import apodrating.API_KEY_HEADER
import apodrating.LOCATION_HEADER
import apodrating.REMOTE_PROXY_SERVICE_ADDRESS
import apodrating.model.*
import apodrating.remoteproxy.RemoteProxyServiceFactory
import apodrating.remoteproxy.reactivex.RemoteProxyService
import io.reactivex.Maybe
import io.reactivex.schedulers.Schedulers
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.api.OperationRequest
import io.vertx.ext.web.api.OperationResponse
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.jdbc.JDBCClient
import org.apache.http.HttpStatus
import org.apache.logging.log4j.kotlin.Logging
import java.sql.SQLIntegrityConstraintViolationException

class ApodQueryServiceImpl(
        private val vertx: Vertx,
        private val config: JsonObject,
        private val apodConfig: ApodRatingConfiguration = ApodRatingConfiguration(config),
        private val jdbc: JDBCClient = JDBCClient.createShared(vertx, apodConfig.toJdbcConfig()),
        private val proxyService: RemoteProxyService =
                RemoteProxyServiceFactory.createProxy(vertx.delegate, REMOTE_PROXY_SERVICE_ADDRESS)
) : ApodQueryService {

    companion object : Logging

    override fun getApodTitle(
            apodId: String,
            context: OperationRequest,
            resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService {
        jdbc.rxQuerySingleWithParams("SELECT ID, DATE_STRING FROM APOD WHERE ID=?",
                json { array(apodId) }
        ).flatMap {
            proxyService.rxPerformApodQuery(
                    it.getInteger(0).toString(),
                    it.getString(1),
                    context.headers.get(API_KEY_HEADER)
            ).subscribeOn(Schedulers.io()).toMaybe()
        }.observeOn(Schedulers.computation())
                .map {
                    succeed(
                            HttpStatus.SC_OK,
                            JsonObject.mapFrom(TextObject(asApod(it).title))
                    )
                }
                .switchIfEmpty(handleApodNotFound())
                .subscribeOn(Schedulers.io())
                .subscribe(resultHandler::handle) { handleFailure(resultHandler, it) }
        return this
    }

    /**
     * Handle a GET request for a single APOD in our database.
     */
    override fun getApodForId(
            apodId: String,
            context: OperationRequest,
            resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService {
        jdbc.rxQuerySingleWithParams("SELECT ID, DATE_STRING FROM APOD WHERE ID=?",
                json { array(apodId) }
        ).flatMap {
            proxyService.rxPerformApodQuery(
                    it.getInteger(0).toString(),
                    it.getString(1),
                    context.headers.get(API_KEY_HEADER)
            ).subscribeOn(Schedulers.io()).toMaybe()
        }.observeOn(Schedulers.computation())
                .map { succeed(HttpStatus.SC_OK, asApod(it).toJsonObject()) }
                .switchIfEmpty(handleApodNotFound())
                .subscribeOn(Schedulers.io())
                .subscribe(resultHandler::handle) { handleFailure(resultHandler, it) }
        return this
    }

    /**
     * Create a new apod in our database and return the resource's location in a http header.
     */
    override fun postApod(
            body: JsonObject,
            context: OperationRequest,
            resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService {
        val apodRequest = asApodRequest(body)
        jdbc.rxUpdateWithParams("INSERT INTO APOD (DATE_STRING) VALUES ?", json { array(apodRequest.dateString) })
                .map { it.keys.get<Int>(0) }
                .flatMap {
                    proxyService.rxPerformApodQuery(
                            it.toString(),
                            apodRequest.dateString,
                            context.headers[API_KEY_HEADER]
                    ).subscribeOn(Schedulers.io())
                }
                .observeOn(Schedulers.computation())
                .map { asApod(it) }
                .map { succeed(HttpStatus.SC_CREATED, LOCATION_HEADER, "/apod/${it.id}") }
                .onErrorReturn {
                    when (it) {
                        is SQLIntegrityConstraintViolationException -> succeed(HttpStatus.SC_CONFLICT)
                        else -> fail(HttpStatus.SC_CONFLICT, it.localizedMessage)
                    }
                }
                .subscribeOn(Schedulers.io())
                .subscribe(resultHandler::handle) {
                    handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
                }
        return this
    }

    /**
     * Handle a GET request for all APODs in our database.
     *
     */
    override fun getApods(
            context: OperationRequest,
            resultHandler: Handler<AsyncResult<OperationResponse>>
    ): ApodQueryService {
        jdbc.rxQuery("SELECT ID, DATE_STRING FROM APOD ")
                .map { it.rows.toList() }
                .filter { it.isEmpty().not() }
                .flattenAsFlowable { it }
                .parallel()
                .flatMap {
                    proxyService.rxPerformApodQuery(
                            it.getInteger("ID").toString(),
                            it.getString("DATE_STRING"),
                            context.headers.get(API_KEY_HEADER)
                    ).toFlowable()
                }
                .map { asApod(it) }
                .filter { !it.isEmpty() }
                .sequential()
                .toList()
                .toMaybe()
                .map { Future.succeededFuture(OperationResponse.completedWithJson(Json.array(it))) }
                .switchIfEmpty(Maybe.just(Future.succeededFuture(OperationResponse.completedWithJson(JsonArray()))))
                .subscribe(resultHandler::handle) {
                    handleFailure(resultHandler, it, HttpStatus.SC_INTERNAL_SERVER_ERROR)
                }
        return this
    }

    /**
     * close resources
     */
    override fun close() {
        jdbc.close()
    }
}

