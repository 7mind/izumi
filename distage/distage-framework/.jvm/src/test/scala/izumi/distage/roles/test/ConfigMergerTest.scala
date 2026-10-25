package izumi.distage.roles.test

import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.{ConfigLoadResult, ConfigSource}
import izumi.distage.framework.services.{ConfigFilteringStrategy, ConfigMerger}
import izumi.distage.roles.test.fixtures.CapturingFixture3Sink
import izumi.logstage.api.{IzLogger, Log}
import org.scalatest.wordspec.AnyWordSpec

class ConfigMergerTest extends AnyWordSpec {

  private def resource(name: String, hocon: String): ConfigLoadResult.Success = {
    ConfigLoadResult.Success(name, ConfigSource.Resource(name), isExplicit = false, ConfigFactory.parseString(hocon))
  }

  private def conflictWarnings(configs: List[ConfigLoadResult.Success]): Seq[String] = {
    val sink = new CapturingFixture3Sink
    val merger = new ConfigMerger.ConfigMergerImpl(
      IzLogger(Log.Level.Trace, sink),
      enableConfigEnvOverrides = false,
      ConfigFilteringStrategy(identity, identity),
    )
    merger.foldConfigs(configs)
    sink.fetch().filter(_.context.dynamic.level == Log.Level.Warn).flatMap(_.message.args.map(_.value.toString))
  }

  "ConfigMerger" should {
    "not report reference config keys defined with equal values in several resources" in {
      val warnings = conflictWarnings(
        List(
          resource("a-reference.conf", "shared { int = 1, str = \"x\", list = [1, 2], nested { flag = true } }"),
          resource("b-reference.conf", "shared { int = 1, str = \"x\", list = [1, 2], nested { flag = true } }"),
        )
      )
      assert(warnings.isEmpty)
    }

    "report only the reference config keys defined with different values in several resources" in {
      val warnings = conflictWarnings(
        List(
          resource("a-reference.conf", "shared { same = 1, different = 1 }"),
          resource("b-reference.conf", "shared { same = 1, different = 2 }"),
        )
      )
      assert(warnings.size == 1)
      assert(warnings.head.contains("shared.different"))
      assert(!warnings.head.contains("shared.same"))
    }
  }
}
