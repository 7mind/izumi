package izumi.fundamentals.typesafe.config

import com.typesafe.config.ConfigValueFactory
import org.scalatest.WordSpec

import scala.concurrent.duration._
import scala.util.Success

class ConfigTest extends WordSpec {
  import ConfigReaderInstances._
  "ConfigReader" should {
    "parse durations" in {

      assert(durationConfigReader.apply(ConfigValueFactory.fromAnyRef("1 second")) == Success(1.second))
      assert(durationConfigReader.apply(ConfigValueFactory.fromAnyRef("2 seconds")) == Success(2.second))
      assert(durationConfigReader.apply(ConfigValueFactory.fromAnyRef("Inf")) == Success(Duration.Inf))

      assert(finiteDurationConfigReader.apply(ConfigValueFactory.fromAnyRef("1 second")) == Success(1.second))
      assert(finiteDurationConfigReader.apply(ConfigValueFactory.fromAnyRef("2 seconds")) == Success(2.second))
      assert(finiteDurationConfigReader.apply(ConfigValueFactory.fromAnyRef("Inf")).isFailure)

    }
  }
}

