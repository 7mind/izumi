package izumi.distage.testkit.runner.services

import izumi.distage.testkit.DebugProperties
import izumi.logstage.api.Log

class TestkitLogging {
  def enableDebugOutput: Boolean = TestkitLogging.enableDebugOutput

  def testkitDebugMessagesLogLevel(forceDebugOutput: Boolean): Log.Level = {
    if (enableDebugOutput || forceDebugOutput) {
      Log.Level.Info
    } else {
      Log.Level.Debug
    }
  }
}

object TestkitLogging {
  private final val enableDebugOutput: Boolean = DebugProperties.`izumi.distage.testkit.debug`.boolValue(false)

}
