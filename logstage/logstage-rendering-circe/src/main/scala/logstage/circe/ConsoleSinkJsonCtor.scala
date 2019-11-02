package logstage.circe

import izumi.logstage.sink.ConsoleSink

final class ConsoleSinkJsonCtor(private val consoleSink: ConsoleSink.type) extends AnyVal {

  def json(prettyPrint: Boolean = false): ConsoleSink = {
    consoleSink(new LogstageCirceRenderingPolicy(prettyPrint))
  }

}
