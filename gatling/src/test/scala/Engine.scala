import apodrating.Configuration
import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

object Engine extends App {

  val props = new GatlingPropertiesBuilder
  props.dataDirectory(IDEPathHelper.dataDirectory.toString)
  props.resultsDirectory(IDEPathHelper.resultsDirectory.toString)
  props.bodiesDirectory(IDEPathHelper.bodiesDirectory.toString)
  props.binariesDirectory(IDEPathHelper.mavenBinariesDirectory.toString)
  props.simulationClass(Configuration.simulationClass)

  Gatling.fromMap(props.build)
}
