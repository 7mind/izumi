package izumi.logstage.api

import izumi.distage.testkit.runner.spec.AnyWordSpec

class LoggingModelTest extends AnyWordSpec {

  "Log levels" should {
    "be comparable" in {
      assert(Log.Level.Debug < Log.Level.Error)
      assert(Log.Level.Debug <= Log.Level.Debug)
    }
  }

}
