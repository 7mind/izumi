package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogContext
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderedParameter, RenderingPolicy}
import io.circe._
import io.circe.syntax._

import scala.runtime.RichInt

class LogstageCirceRenderingPolicy(prettyPrint: Boolean = false) extends RenderingPolicy {
  override def render(entry: Log.Entry): String = {
    import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._

    val formatted = LogUnit.formatMessage(entry, withColors = false)

    val params = parametersToJson(formatted.parameters)
    val context = contextToJson(mkMap(entry.context.customContext.values))


    val eventInfo = Map(
      "class" -> Json.fromString(new RichInt(formatted.template.hashCode).toHexString),
      "logger" -> Json.fromString(entry.context.static.id.id),
      "line" -> Json.fromInt(entry.context.static.position.line),
      "file" -> Json.fromString(entry.context.static.position.file),
      "level" -> Json.fromString(entry.context.dynamic.level.toString.toLowerCase),
      "timestamp" -> Json.fromLong(entry.context.dynamic.tsMillis),
      "datetime" -> Json.fromString(entry.context.dynamic.tsMillis.asEpochMillisUtc.isoFormatUtc),
      "thread" -> Json.fromFields(Seq(
        "id" -> Json.fromLong(entry.context.dynamic.threadData.threadId),
        "name" -> Json.fromString(entry.context.dynamic.threadData.threadName)
      )),
    )

    val messageInfo = Map(
      "@event" -> eventInfo.asJson,
      "@template" -> Json.fromString(formatted.template),
      "@message" -> Json.fromString(formatted.message),
    )

    val parts = if (context.values.nonEmpty) {
      Seq(params, messageInfo, Map("@context" -> context.asJson))
    } else {
      Seq(params, messageInfo)
    }

    val json = parts.reduce(_ ++ _).asJson

    if (prettyPrint) {
      json.pretty(Printer.spaces2)
    } else {
      json.noSpaces
    }
  }

  protected def parametersToJson(params: Seq[RenderedParameter]): Map[String, Json] = {
    val paramGroups = params.groupBy(_.name)
    val (unary, multiple) = paramGroups.partition(_._2.size == 1)
    val paramsMap = unary.map {
      kv =>
        kv._1 -> repr(kv._2.head)
    }
    val multiparamsMap = multiple.map {
      kv =>
        kv._1 -> Json.arr(kv._2.map(repr): _*)
    }
    paramsMap ++ multiparamsMap
  }

  protected def contextToJson(p: Map[String, Set[String]]): Map[String, Json] = {
    val (unary, multiple) = p.partition(_._2.size == 1)
    val paramsMap = unary.map {
      kv =>
        LogUnit.normalizeName(kv._1) -> Json.fromString(kv._2.head)
    }
    paramsMap ++ multiple.mapValues(v => Json.arr(v.toSeq.map(Json.fromString) : _*))
  }

  protected def repr(parameter: RenderedParameter): Json = {
    import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

    parameter match {
      case RenderedParameter(a: Double, _, _, _) =>
        Json.fromDoubleOrNull(a)
      case RenderedParameter(a: BigDecimal, _, _, _) =>
        Json.fromBigDecimal(a)
      case RenderedParameter(a: Int, _, _, _) =>
        Json.fromInt(a)
      case RenderedParameter(a: BigInt, _, _, _) =>
        Json.fromBigInt(a)
      case RenderedParameter(a: Boolean, _, _, _) =>
        Json.fromBoolean(a)
      case RenderedParameter(a: Long, _, _, _) =>
        Json.fromLong(a)
      case RenderedParameter(null, _, _, _) =>
        Json.Null
      case RenderedParameter(a: Iterable[_], _, visibleName, _) =>
        val params = a.map(v => repr(LogUnit.formatArg(visibleName, v, withColors = false))).toList
        Json.arr(params: _*)
      case RenderedParameter(a: Throwable, _, _, _) =>
        Json.fromFields(Seq(
          "type" -> Json.fromString(a.getClass.getName)
          , "message" -> Json.fromString(a.getMessage)
          , "stacktrace" -> Json.fromString(a.stackTrace)
        ))
      case RenderedParameter(_, repr, _, _) =>
        Json.fromString(repr)
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
