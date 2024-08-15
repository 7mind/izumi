package izumi.logstage.api.config

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.logstage.api.Log

trait LogConfigService {
  def acceptable(id: Log.LoggerId, line: Int, logLevel: Log.Level): Boolean
  def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean

  def config(e: Log.Entry): LogEntryConfig
  def validate(fallback: TrivialLogger): Unit
}
