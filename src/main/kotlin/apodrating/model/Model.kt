@file:Suppress("TooManyFunctions")

package apodrating.model

import apodrating.FIELD_DATE_STRING
import apodrating.FIELD_ID
import apodrating.FIELD_RATING
import apodrating.FIELD_TITLE
import io.vertx.core.DeploymentOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.config.configRetrieverOptionsOf
import io.vertx.kotlin.config.configStoreOptionsOf
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.Vertx

/**
 * Convert this asApod into a JsonObject.
 */
fun Apod.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Convert this asApod into a String encoded JsonObject.
 */
fun Apod.toJsonString(): String = this.toJsonObject().encode()

/**
 * Create a new asApod from an ID String and a JsonObject.
 */
fun asApod(id: String, jsonObject: JsonObject): Apod = Apod(
    id = id,
    dateString = jsonObject.getString("date"),
    title = jsonObject.getString(FIELD_TITLE),
    imageUriHd = jsonObject.getString("hdurl")
)

/**
 * Create a new asApod from a JsonObject.
 */
fun asApod(jsonObject: JsonObject): Apod = Apod(
    id = jsonObject.getString(FIELD_ID),
    dateString = jsonObject.getString(FIELD_DATE_STRING),
    title = jsonObject.getString(FIELD_TITLE),
    imageUriHd = jsonObject.getString("imageUriHd")
)

/**
 * Return an empty asApod object.
 */
fun emptyApod(): Apod = Apod(id = "", title = "", dateString = "", imageUriHd = "")

/**
 * Return true if this is an empty asApod.
 */
fun Apod.isEmpty() = this == emptyApod()

/**
 * Create an asApodRequest from a JsonObject
 */
fun asApodRequest(jsonObject: JsonObject): ApodRequest =
    ApodRequest(dateString = jsonObject.getString(FIELD_DATE_STRING))

/**
 * Convert this asRating into a JsonObject.
 */
fun Rating.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Convert this asRating into a String encoded JsonObject.
 */
fun Rating.toJsonString(): String = this.toJsonObject().encode()

/**
 * Create a asRating from a JsonObject
 */
fun asRating(jsonObject: JsonObject): Rating =
    Rating(id = jsonObject.getInteger(FIELD_ID), rating = jsonObject.getInteger(FIELD_RATING))

/**
 * Create a asRating from a ResultSet
 */
fun asRating(result: ResultSet): Rating = asRating(json {
    obj(FIELD_ID to result.rows[0]["APOD_ID"], FIELD_RATING to result.rows[0]["VALUE"])
})

/**
 * Create a asRatingRequest from a JsonObject
 */
fun asRatingRequest(jsonObject: JsonObject): RatingRequest = RatingRequest(rating = jsonObject.getInteger(FIELD_RATING))

/**
 * Convert this Error into a JsonObject.
 */
fun Error.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Error this asRating into a String encoded JsonObject.
 */
fun Error.toJsonString(): String = this.toJsonObject().encode()

/**
 * Get deployment options configured by environment variables.
 */
fun deploymentOptionsFromEnv(vertx: Vertx): DeploymentOptions = configRetrieverOptionsOf(
    scanPeriod = 2000,
    stores = listOf(configStoreOptionsOf(type = "env"))
).deploymentOptions(vertx)

/**
 * Get a JsonObject for an apod query that is going to be sent over the eventbus.
 */
fun apodQueryParameters(id: String, date: String, apiKey: String): JsonObject = JsonObject().put(FIELD_ID, id)
    .put("date", date)
    .put("nasaApiKey", apiKey)