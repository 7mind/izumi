package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogContext
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods

import scala.runtime.RichInt

class JsonRenderingPolicy() extends RenderingPolicy {
  // TODO: shitty inheritance
  protected final val stringPolicy = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false))

  override def render(entry: Log.Entry): String = {
    val formatted = LogUnit.formatMessage(entry, withColors = false)

    val params = makeJson(formatted.parameters)
    val context = makeJson(mkMap(entry.context.customContext.values))


    val messageInfo = ("@event" ->
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
          entry.context.dynamic.tsMillis.asEpochMillisUtc.isoFormatUtc
        })
      ) ~
      ("@template" -> formatted.template) ~
      ("@message" -> formatted.message)

    val parts: Seq[JObject] = if (context.values.nonEmpty) {
      Seq(messageInfo, "@context" -> context, params)
    } else {
      Seq(messageInfo, params)
    }

    val json = parts.reduce(_ ~ _)

    JsonMethods.compact(JsonMethods.render(json))
  }

  private def makeJson(p: Map[String, Set[String]]): JObject = {
    val (unary, multiple) = p.partition(_._2.size == 1)
    (unary.mapValues(_.head): JObject) ~ multiple
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
