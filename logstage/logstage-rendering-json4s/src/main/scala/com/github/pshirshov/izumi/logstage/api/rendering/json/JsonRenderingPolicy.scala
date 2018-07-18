package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogContext
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderedParameter, RenderingPolicy}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods

import scala.runtime.RichInt

class JsonRenderingPolicy(prettyPrint: Boolean = false) extends RenderingPolicy {
  override def render(entry: Log.Entry): String = {
    val formatted = LogUnit.formatMessage(entry, withColors = false)

    val params = parametersToJson(formatted.parameters)
    val context = contextToJson(mkMap(entry.context.customContext.values))


    val messageInfo = ("@event" ->
      ("class" -> new RichInt(formatted.template.hashCode).toHexString) ~
        ("logger" -> entry.context.static.id.id) ~
        ("line" -> entry.context.static.position.line) ~
        ("file" -> entry.context.static.position.file) ~
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
      Seq(params, messageInfo, "@context" -> context)
    } else {
      Seq(params, messageInfo)
    }

    val json = parts.reduce(_ ~ _)

    val rendered = JsonMethods.render(json)
    if (prettyPrint) {
      JsonMethods.pretty(rendered)
    } else {
      JsonMethods.compact(rendered)
    }
  }

  protected def parametersToJson(params: Seq[RenderedParameter]): JObject = {
    val paramGroups = params.groupBy(_.name)
    val (unary, multiple) = paramGroups.partition(_._2.size == 1)
    val paramsMap = unary.map {
      kv =>
        JField(kv._1, repr(kv._2.head))
    }
    val multiparamsMap = multiple.map {
      kv =>
        JField(kv._1, kv._2.map(repr))
    }
    (paramsMap: JObject) ~ (multiparamsMap: JObject)
  }

  protected def contextToJson(p: Map[String, Set[String]]): JObject = {
    val (unary, multiple) = p.partition(_._2.size == 1)
    val paramsMap = unary.map {
      kv =>
        JField(LogUnit.normalizeName(kv._1), kv._2.head)
    }
    (paramsMap: JObject) ~ multiple
  }

  protected def repr(parameter: RenderedParameter): JValue = {
    parameter match {
      case RenderedParameter(a: Double, _, _, _) =>
        JDouble(a)
      case RenderedParameter(a: BigDecimal, _, _, _) =>
        JDecimal(a)
      case RenderedParameter(a: Int, _, _, _) =>
        JInt(a)
      case RenderedParameter(a: BigInt, _, _, _) =>
        JInt(a)
      case RenderedParameter(a: Boolean, _, _, _) =>
        JBool(a)
      case RenderedParameter(a: Long, _, _, _) =>
        JLong(a)
      case RenderedParameter(null, _, _, _) =>
        JNull
      case RenderedParameter(a: Iterable[_], _, visibleName, _) =>
        val params = a.map(v => repr(LogUnit.formatArg(visibleName, v, withColors = false))).toList
        JArray(params)
      case RenderedParameter(a: Throwable, _, _, _) =>
        import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._
        Map(
          "type" -> a.getClass.getName
          , "message" -> a.getMessage
          , "stacktrace" -> a.stackTrace
        ): JObject
      case RenderedParameter(_, repr, _, _) =>
        JString(repr)
    }
  }

  protected def mkMap(values: LogContext): Map[String, Set[String]] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val customContext = values
      .map(kv => (kv.name, kv.value))
      .toMultimap
      .map {
        case (k, v) =>
          k -> v.map(_.toString)
      }
    customContext
  }


}
