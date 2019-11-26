package izumi.logstage.api.rendering.json

import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.logstage.api.Log
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.logunits.LogFormat
import izumi.logstage.api.rendering.{RenderedParameter, RenderingPolicy}
import io.circe._
import io.circe.syntax._

import scala.collection.compat.immutable.ArraySeq
import scala.collection.mutable
import scala.runtime.RichInt

class LogstageCirceRenderingPolicy(prettyPrint: Boolean = false) extends RenderingPolicy {
  import LogstageCirceRenderingPolicy._

  override def render(entry: Log.Entry): String = {
    import izumi.fundamentals.platform.time.IzTime._

    val result = mutable.ArrayBuffer[(String, Json)]()

    val formatted = Format.formatMessage(entry, withColors = false)
    val params = parametersToJson[RenderedParameter](ArraySeq.empty ++ (formatted.parameters ++ formatted.unbalanced), _.normalizedName, repr)
    if (params.nonEmpty) {
      result += "event" -> params.asJson
    }

    val ctx = parametersToJson[LogArg](entry.context.customContext.values, _.name, v => {
      val p = Format.formatArg(v, withColors = false)
      repr(p)
    })

    if (ctx.nonEmpty) {
      result += "context" -> ctx.asJson
    }

    val eventInfo = Json.fromFields(Seq(
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
    ))

    val tail = Seq(
      "meta" -> eventInfo,
      "text" -> Json.fromFields(Seq(
        "template" -> Json.fromString(formatted.template),
        "message" -> Json.fromString(formatted.message),
      ))
    )
    result ++= tail

    val json = Json.fromFields(result)

    if (prettyPrint) {
      json.printWith(Printer.spaces2)
    } else {
      json.noSpaces
    }
  }

  @inline private[this] def parametersToJson[T](params: Seq[T], name: T => String, repr: T => Json): Map[String, Json] = {
    val paramGroups = params.groupBy(name)
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

  @inline private[this] def repr(parameter: RenderedParameter): Json = {
    val mapStruct: PartialFunction[Any, Json] = {
      case a: Iterable[_] =>
        val params = a
          .map {
            v =>
              mapListElement.apply(v)
          }
          .toList
        Json.arr(params: _*)
      case _ =>
        Json.fromString(parameter.repr)
    }

    val mapParameter = mapScalar orElse mapStruct

    mapParameter(parameter.value)
  }

  private val mapScalar: PartialFunction[Any, Json] = {
    case null =>
      Json.Null
    case a: Json =>
      a
    case a: Double =>
      Json.fromDoubleOrNull(a)
    case a: BigDecimal =>
      Json.fromBigDecimal(a)
    case a: Int =>
      Json.fromInt(a)
    case a: BigInt =>
      Json.fromBigInt(a)
    case a: Boolean =>
      Json.fromBoolean(a)
    case a: Long =>
      Json.fromLong(a)
    case a: Throwable =>
      Json.fromFields(Seq(
        "type" -> Json.fromString(a.getClass.getName)
        , "message" -> Json.fromString(a.getMessage)
        , "stacktrace" -> Json.fromString(a.stackTrace)
      ))
  }

  private val mapToString: PartialFunction[Any, Json] = {
    case o => Json.fromString(o.toString)
  }

  private val mapListElement = mapScalar orElse mapToString
}

object LogstageCirceRenderingPolicy {
  object Format extends LogFormat.LogFormatImpl {
    override protected def toString(argValue: Any): String = {
      argValue match {
        case j: Json =>
          j.noSpaces
        case o => o.toString
      }
    }
  }
}
