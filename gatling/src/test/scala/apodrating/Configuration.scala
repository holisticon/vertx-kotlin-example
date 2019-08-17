package apodrating

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.{HttpProtocolBuilder, HttpProxyBuilder}

import scala.util.Properties

object Configuration {

  // PROXY CONF
  private val proxyUrl: String = System.getProperty("proxyUrl")
  private val proxyPort: Int = System.getProperty("proxyPort", "8080").toInt
  private val proxyUsername: String = System.getProperty("proxyUsername")
  private val proxyPassword: String = System.getProperty("proxyPassword")

  // What to test
  private val baseUrl: String = Properties.envOrElse("BASE_URL", Properties.propOrElse("baseUrl", "https://localhost:8080"))

  // Test Load Config
  val usersPerSecond: Int = Properties.envOrElse("USERS_PER_SECOND", Properties.propOrElse("usersPerSecond", "2")).toInt
  val injectionTime: Int = Properties.envOrElse("INJECTION_TIME", Properties.propOrElse("injectionTime", "5")).toInt
  val repeats: Int = Properties.envOrElse("TEST_REPEAT", Properties.propOrElse("testRepeat", "1")).toInt

  val authHeader: String = Properties.envOrElse("NASA_API_KEY", Properties.propOrElse("authHeader", "secret_header"))
  val simulationClass: String = Properties.envOrElse("SIMULATION_CLASS", Properties.propOrElse("simulationClass", "ApodSimulation"))

  val apodFeeder = csv("apod.csv")

  private def setupProxy(): HttpProtocolBuilder = {

    if (Configuration.proxyUrl != null && Configuration.proxyUrl.length() > 0) {
      val proxy: HttpProxyBuilder = Proxy(Configuration.proxyUrl, Configuration.proxyPort)
      if (Configuration.proxyUsername != null) {
        proxy.credentials(Configuration.proxyUsername, Configuration.proxyPassword)
      }

      return http.proxy(proxy)
    }

    return http
  }

  val httpConfiguration: HttpProtocolBuilder = setupProxy()
    .baseURL(Configuration.baseUrl)

}
