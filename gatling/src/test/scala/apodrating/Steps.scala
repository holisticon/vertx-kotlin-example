package apodrating

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

object Steps {


  val getExistingApods: HttpRequestBuilder = http("GET existing apod")
    .get("/apod")
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

}
