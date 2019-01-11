package apodrating.model

import io.vertx.core.json.JsonObject
import io.vertx.ext.sql.ResultSet
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

fun Apod.toJsonObject(): JsonObject = JsonObject.mapFrom(this)
fun Apod.toJsonString(): String = this.toJsonObject().encode()
fun Apod(id: String, jsonObject: JsonObject): Apod = Apod(
    id = id,
    dateString = jsonObject.getString("date"),
    title = jsonObject.getString("title"),
    imageUriHd = jsonObject.getString("hdurl")
)

fun emptyApod(): Apod = Apod(id = "", title = "", dateString = "", imageUriHd = "")
fun Apod.isEmpty() = this == emptyApod()

fun ApodRequest(jsonObject: JsonObject): ApodRequest = ApodRequest(dateString = jsonObject.getString("dateString"))

fun Rating.toJsonObject(): JsonObject = JsonObject.mapFrom(this)
fun Rating.toJsonString(): String = this.toJsonObject().encode()
fun Rating(jsonObject: JsonObject): Rating =
    Rating(id = jsonObject.getInteger("id"), rating = jsonObject.getInteger("rating"))

fun Rating(result: ResultSet): Rating = Rating(json {
    obj("id" to result.rows[0]["APOD_ID"], "rating" to result.rows[0]["VALUE"])
})

fun RatingRequest(jsonObject: JsonObject): RatingRequest = RatingRequest(rating = jsonObject.getInteger("rating"))

fun Error.toJsonObject(): JsonObject = JsonObject.mapFrom(this)
fun Error.toJsonString(): String = this.toJsonObject().encode()