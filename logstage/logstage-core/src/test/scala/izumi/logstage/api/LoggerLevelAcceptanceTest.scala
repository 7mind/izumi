package izumi.logstage.api

import org.scalatest.wordspec.AnyWordSpec

class LoggerLevelAcceptanceTest extends AnyWordSpec {
  "logger router" should {
    "properly handle exact rules" in {
      import logstage.*

      val levelsMap = Map(
        "org.test" -> Log.Level.Debug,
        "org.test2.FullName" -> Log.Level.Debug,
      )

      val logger = IzLogger(threshold = Log.Level.Info, levels = levelsMap)

      assert(logger.router.acceptable(Log.LoggerId("org.test"), Log.Level.Debug))
      assert(logger.router.acceptable(Log.LoggerId("org.test.other"), Log.Level.Debug))
      assert(logger.router.acceptable(Log.LoggerId("org.test.other.sub"), Log.Level.Debug))
      assert(logger.router.acceptable(Log.LoggerId("org.test2.FullName"), Log.Level.Debug))
    }
  }
}
