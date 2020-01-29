package logstage.circe

import izumi.logstage.api.rendering.json
import izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

trait LogstageCirce {

  type LogstageCirceRenderingPolicy = json.LogstageCirceRenderingPolicy
  val LogstageCirceRenderingPolicy: json.LogstageCirceRenderingPolicy.type = json.LogstageCirceRenderingPolicy

  @inline implicit final def ToConsoleSinkJsonCtor(consoleSink: ConsoleSink.type): ConsoleSinkJsonCtor = new ConsoleSinkJsonCtor(consoleSink)

}

