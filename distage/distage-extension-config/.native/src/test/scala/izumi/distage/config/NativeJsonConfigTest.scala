package izumi.distage.config

import io.circe.{Json, JsonObject}
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.testkit.runner.spec.AnyWordSpec

final class NativeJsonConfigTest extends AnyWordSpec {
  "Native JSON configuration" should {
    "evaluate the default only when the requested path is absent" in {
      val config = JsonObject("value" -> Json.fromInt(7), "invalid" -> Json.fromString("invalid"))
      var defaults = 0
      def default: Int = {
        defaults += 1
        42
      }
      val reader = DIConfigReader[Int]
      assert(reader.decodeConfigWithDefault("value")(default)(config) == 7)
      assert(defaults == 0)
      assert(reader.decodeConfigWithDefault("missing")(default)(config) == 42)
      assert(defaults == 1)
      val error = intercept[DIConfigReadException] {
        reader.decodeConfigWithDefault("invalid")(default)(config)
      }
      assert(error.getMessage.contains("invalid"))
      assert(defaults == 1)
    }

    "report missing values and unsupported quoted paths through the configuration error" in {
      val reader = DIConfigReader[Int]
      val missing = intercept[DIConfigReadException] {
        reader.decodeConfig("missing")(DistageConfigImpl.empty)
      }
      assert(missing.getMessage.contains("missing"))
      val quoted = intercept[DIConfigReadException] {
        reader.decodeConfig("\"value\"")(JsonObject("value" -> Json.fromInt(7)))
      }
      assert(quoted.getCause.isInstanceOf[IllegalArgumentException])
      assert(quoted.getCause.getMessage.contains("Scala Native"))
    }

    "merge nested JSON fallbacks with explicit values taking precedence" in {
      val config = JsonObject("service" -> Json.obj("port" -> Json.fromInt(8080)))
      val fallback = JsonObject("service" -> Json.obj("port" -> Json.fromInt(80), "host" -> Json.fromString("localhost")))
      val merged = DistageConfigImpl.withFallback(config, fallback)
      assert(DIConfigReader[Int].decodeConfig("service.port")(merged) == 8080)
      assert(DIConfigReader[String].decodeConfig("service.host")(merged) == "localhost")
      assert(DistageConfigImpl.allKeys(merged) == Set("service", "service.port", "service.host"))
      assert(!DistageConfigImpl.hasPath(config, "service.host"))
      assert(DIConfigReader[Int].decodeConfig("service.port")(fallback) == 80)
    }
  }
}
