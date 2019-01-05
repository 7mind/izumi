package logstage

import com.github.pshirshov.izumi.logstage.api.rendering.json

package object circe extends LogstageCirce {

  override type LogstageCirceRenderingPolicy = json.LogstageCirceRenderer

}
