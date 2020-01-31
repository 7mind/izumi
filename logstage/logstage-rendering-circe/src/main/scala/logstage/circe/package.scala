package logstage

import izumi.logstage.api.rendering.json

package object circe extends LogstageCirce {

  override type LogstageCirceRenderingPolicy = json.LogstageCirceRenderingPolicy
  override val LogstageCirceRenderingPolicy: json.LogstageCirceRenderingPolicy.type = json.LogstageCirceRenderingPolicy

  override implicit val LogstageCirceJsonCodec: LogstageCirceCodec.LogstageCirceJsonCodec.type = LogstageCirceCodec.LogstageCirceJsonCodec
}
