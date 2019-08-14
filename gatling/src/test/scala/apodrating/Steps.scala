package apodrating

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

object Steps {


  val getExistingApods: HttpRequestBuilder = http("GET existing apod")
    .get("/apod")
    .header("X-API-KEY", "toSgQoGTapxfoNs40DFk9Z2Z7YfgcuYNcZtZuT1S")
    .check(status.in(200))


}
