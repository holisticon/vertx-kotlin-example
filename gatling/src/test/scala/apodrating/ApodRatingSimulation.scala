package apodrating

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class ApodRatingSimulation extends Simulation {

  val scn: ScenarioBuilder = scenario("APOD API")
    .feed(Configuration.apodFeeder.random)
    .exec(Steps.postNewApod)
    .repeat(Configuration.repeats) {
      exec(Steps.getExistingApods)
        .pause(1)
        .exec(Steps.putRating)
        .pause(1)
        .exec(Steps.getExistingApod)
        .pause(1)
        .exec(Steps.putRating)
    }
    .repeat(Configuration.repeats) {
      exec(Steps.getExistingApodRating)
    }

  setUp(
    scn.inject(constantUsersPerSec(Configuration.usersPerSecond) during (Configuration.injectionTime seconds))
  ).protocols(Configuration.httpConfiguration)

}
