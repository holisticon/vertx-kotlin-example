package apodrating.model

import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

/**
 * Convert this Apod into a JsonObject.
 */
fun Apod.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Convert this Apod into a String encoded JsonObject.
 */
fun Apod.toJsonString(): String = this.toJsonObject().encode()

/**
 * Create a new Apod from an ID String and a JsonObject.
 */
fun Apod(id: String, jsonObject: JsonObject): Apod = Apod(
    id = id,
    dateString = jsonObject.getString("date"),
    title = jsonObject.getString("title"),
    imageUriHd = jsonObject.getString("hdurl")
)

/**
 * Return an empty Apod object.
 */
fun emptyApod(): Apod = Apod(id = "", title = "", dateString = "", imageUriHd = "")

/**
 * Return true if this is an empty Apod.
 */
fun Apod.isEmpty() = this == emptyApod()

/**
 * Create an ApodRequest from a JsonObject
 */
fun ApodRequest(jsonObject: JsonObject): ApodRequest = ApodRequest(dateString = jsonObject.getString("dateString"))

/**
 * Convert this Rating into a JsonObject.
 */
fun Rating.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Convert this Rating into a String encoded JsonObject.
 */
fun Rating.toJsonString(): String = this.toJsonObject().encode()

/**
 * Create a Rating from a JsonObject
 */
fun Rating(jsonObject: JsonObject): Rating =
    Rating(id = jsonObject.getInteger("id"), rating = jsonObject.getInteger("rating"))

/**
 * Create a Rating from a ResultSet
 */
fun Rating(result: ResultSet): Rating = Rating(json {
    obj("id" to result.rows[0]["APOD_ID"], "rating" to result.rows[0]["VALUE"])
})

/**
 * Create a RatingRequest from a JsonObject
 */
fun RatingRequest(jsonObject: JsonObject): RatingRequest = RatingRequest(rating = jsonObject.getInteger("rating"))

/**
 * Convert this Error into a JsonObject.
 */
fun Error.toJsonObject(): JsonObject = JsonObject.mapFrom(this)

/**
 * Error this Rating into a String encoded JsonObject.
 */
fun Error.toJsonString(): String = this.toJsonObject().encode()