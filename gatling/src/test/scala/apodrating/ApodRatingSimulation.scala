package apodrating

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class ApodRatingSimulation extends Simulation {

  val scn: ScenarioBuilder = scenario("APOD API")
    .repeat(Configuration.repeats) {
      exec(Steps.getExistingApods)
        .pause(1)
    }

  setUp(
    scn.inject(constantUsersPerSec(Configuration.usersPerSecond) during (Configuration.injectionTime seconds))
  ).protocols(Configuration.httpConfiguration)

}
