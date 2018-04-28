package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.logger.RenderingOptions
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogContext
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy}
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods

import scala.runtime.RichInt

class JsonRenderingPolicy() extends RenderingPolicy {
  // TODO: shitty inheritance
  protected final val stringPolicy = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false))

  override def render(entry: Log.Entry): String = {
    val formatted = stringPolicy.formatMessage(entry)
    val json =
      ("@event" ->
        ("class" -> new RichInt(formatted.template.hashCode).toHexString) ~
          ("logger" -> entry.context.static.id.id) ~
          ("line" -> entry.context.static.line) ~
          ("file" -> entry.context.static.file) ~
          ("thread" ->
            ("id" -> entry.context.dynamic.threadData.threadId) ~
              ("name" -> entry.context.dynamic.threadData.threadName)
            ) ~
          ("level" -> entry.context.dynamic.level.toString.toLowerCase) ~
          ("timestamp" -> entry.context.dynamic.tsMillis) ~
          ("datetime" -> {
            import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
            entry.context.dynamic.tsMillis.asEpochMillis.isoFormatUtc
          })
        ) ~
        ("@context" -> mkMap(entry.context.customContext.values)) ~
        ("@template" -> formatted.template) ~
        ("@message" -> formatted.message) ~
        mkMap(entry.message.args)


    JsonMethods.compact(JsonMethods.render(json))
  }

  protected def mkMap(values: LogContext): Map[String, Set[String]] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val customContext = values.toMultimap.map {
      case (k, v) =>
        k -> v.map(_.toString)
    }
    customContext
  }
}
