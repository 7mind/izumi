package izumi.logstage.api.config

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.api.Log

trait LogConfigService {
  def acceptable(id: Log.LoggerId, logLevel: Log.Level): Boolean
  def acceptable(position: CodePosition, logLevel: Log.Level): Boolean

  def config(e: Log.Entry): LogEntryConfig
  def validate(fallback: TrivialLogger): Unit
}
