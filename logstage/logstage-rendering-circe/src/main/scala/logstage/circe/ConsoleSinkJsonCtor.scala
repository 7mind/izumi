package logstage.circe

import izumi.logstage.sink.ConsoleSink

final class ConsoleSinkJsonCtor(private val consoleSink: ConsoleSink.type) extends AnyVal {

  @deprecated("Deprecated because this method requires a wildcard import to use. Use `logstage.ConsoleSink(logstage.circe.LogstageCirceRenderingPolicy(prettyPrint))`", "since 0.10.1")
  def json(prettyPrint: Boolean = false): ConsoleSink = {
    consoleSink(LogstageCirceRenderingPolicy(prettyPrint))
  }

}
