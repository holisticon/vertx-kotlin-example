@file:Suppress("TooManyFunctions")

package apodrating.model

import apodrating.FIELD_DATE_STRING
import apodrating.FIELD_ID
import apodrating.FIELD_RATING
import apodrating.FIELD_TITLE
import io.reactivex.Single
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.reactivex.core.Vertx

fun Apod.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

fun asApod(id: String, jsonObject: JsonObject): Apod = Apod(
    id = id,
    dateString = jsonObject.getString("date"),
    title = jsonObject.getString(FIELD_TITLE),
    imageUriHd = jsonObject.getString("hdurl")
)

fun asApod(jsonObject: JsonObject): Apod = Apod(
    id = jsonObject.getString(FIELD_ID),
    dateString = jsonObject.getString(FIELD_DATE_STRING),
    title = jsonObject.getString(FIELD_TITLE),
    imageUriHd = jsonObject.getString("imageUriHd")
)

fun emptyApod(): Apod = Apod(id = "", title = "", dateString = "", imageUriHd = "")

fun Apod.isEmpty() = this == emptyApod()

fun asApodRequest(jsonObject: JsonObject): ApodRequest =
    ApodRequest(dateString = jsonObject.getString(FIELD_DATE_STRING))

fun Rating.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

fun asRating(jsonObject: JsonObject): Rating =
    Rating(id = jsonObject.getInteger(FIELD_ID), rating = jsonObject.getInteger(FIELD_RATING))

fun asRatingRequest(jsonObject: JsonObject): RatingRequest = RatingRequest(rating = jsonObject.getInteger(FIELD_RATING))

fun Error.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

fun Error.toJsonString(): String = this.toJsonObject().encode()

fun deploymentOptionsFromEnv(vertx: Vertx): Single<DeploymentOptions> {
    return configRetrieverOptionsOf(
        scanPeriod = 2000,
        stores = listOf(configStoreOptionsOf(type = "env"))
    ).deploymentOptions(vertx)
}
