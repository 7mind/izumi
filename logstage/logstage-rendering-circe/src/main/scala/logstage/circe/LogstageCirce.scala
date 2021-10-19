package logstage.circe

import io.circe.Encoder
import izumi.logstage.api.rendering.{LogstageCodec, json}
import izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

trait LogstageCirce {
  type LogstageCirceRenderingPolicy = json.LogstageCirceRenderingPolicy
  val LogstageCirceRenderingPolicy: json.LogstageCirceRenderingPolicy.type = json.LogstageCirceRenderingPolicy

  implicit val LogstageCirceJsonCodec: LogstageCirceCodec.LogstageCirceJsonCodec.type = LogstageCirceCodec.LogstageCirceJsonCodec
  def fromCirce[T: Encoder]: LogstageCodec[T] = new LogstageCirceCodec[T]
}
