package apodrating

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.util.Random

object Steps {

  val r: Random.type = scala.util.Random

  val getExistingApods: HttpRequestBuilder = http("GET existing apod")
    .get("/apod/" + r.nextInt(20))
    .header("X-API-KEY", Configuration.authHeader)
    .check(status.in(200))

  val getExistingApod: HttpRequestBuilder = http("GET list of existing apods")
    .get("/apod")
    .header("X-API-KEY", Configuration.authHeader)
    .check(status.in(200))

  val getExistingApodRating: HttpRequestBuilder = http("GET existing apod rating")
    .get("/apod/" + r.nextInt(20) + "/rating")
    .header("X-API-KEY", Configuration.authHeader)
    .check(status.in(200))

  val postNewApod: HttpRequestBuilder = http("POST new apod")
    .post("/apod")
    .header("X-API-KEY", Configuration.authHeader)
    .body(StringBody(
      """
        | {
        |   "dateString": "${datestring}"
        | }
        | """.stripMargin)).asJSON
    .check(status.in(201, 409))

  val putRating: HttpRequestBuilder = http("PUT rating")
    .put(s"/apod/" + r.nextInt(20) + "/rating")
    .header("X-API-KEY", Configuration.authHeader)
    .body(StringBody(
      s"""
         | {
         |   "rating": ${r.nextInt(9) + 1}
         | }
         | """.stripMargin)).asJSON
    .check(status.in(204))

}
