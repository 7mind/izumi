package logstage.circe

import com.github.pshirshov.izumi.logstage.api.rendering.json
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink

import scala.language.implicitConversions

trait LogstageCirce {

  type LogstageCirceRenderingPolicy = json.LogstageCirceRenderingPolicy

  @inline implicit final def ToConsoleSinkJsonCtor(consoleSink: ConsoleSink.type): ConsoleSinkJsonCtor = new ConsoleSinkJsonCtor(consoleSink)

}

