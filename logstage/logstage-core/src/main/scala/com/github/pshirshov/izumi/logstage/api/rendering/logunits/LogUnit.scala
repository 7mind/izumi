package com.github.pshirshov.izumi.logstage.api.rendering.logunits

import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.ConsoleColors

case class Margin(minimized: Option[Int], elipsed: Boolean, size: Option[Int])

sealed trait LogUnit {

  def aliases: Vector[String]

  def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String

  def undefined(entry: Log.Entry): Boolean
}

object LogUnit {

  def withMargin(string: String, margin: Option[Margin]): String = {
    margin
      .map {
        case m@Margin(Some(v), _, _) =>
          (string.minimize(v), m)
        case m@Margin(None, _, _) =>
          (string, m)
      }.fold(string) {
      case (s, Margin(_, true, Some(pad))) =>
        s.ellipsedLeftPad(pad)
      case (s, Margin(_, _, Some(pad))) =>
        s.leftPad(pad)
      case (s, _) =>
        s
    }
  }

  case object ThreadUnit extends LogUnit {
    override val aliases: Vector[String] = Vector("thread", "t")

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      import entry.context
      val builder = new StringBuilder
      val threadName = s"${context.dynamic.threadData.threadName}:${context.dynamic.threadData.threadId}"
      if (withColors) {
        builder.append(Console.UNDERLINED)
      } else {
        builder.append('[')
      }
      builder.append(threadName.ellipsedLeftPad(15))
      if (withColors) {
        builder.append(Console.RESET)
        builder.append(" ")
      } else {
        builder.append("]")
      }

      withMargin(builder.toString(), margin)

    }

    override def undefined(entry: Log.Entry): Boolean = false
  }

  case object TimestampUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "timestamp", "ts"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      val builder = new StringBuilder
      val context = entry.context
      if (withColors) {
        builder.append(ConsoleColors.logLevelColor(context.dynamic.level))
        builder.append(Console.UNDERLINED)
        builder.append(Console.BOLD)
      }
      val ts = {
        import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
        Instant.ofEpochMilli(context.dynamic.tsMillis).atZone(ZoneId.systemDefault()).isoFormat
      }
      builder.append(ts)
      if (withColors) {
        builder.append(Console.RESET)
      }
      withMargin(builder.toString(), margin)
    }

    override def undefined(entry: Log.Entry): Boolean = false

  }

  case object LevelUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "level", "lvl"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      val builder = new StringBuilder
      val context = entry.context
      if (withColors) {
        builder.append(ConsoleColors.logLevelColor(context.dynamic.level))
        builder.append(Console.UNDERLINED)
        builder.append(Console.BOLD)
      }

      val level = context.dynamic.level.toString
      builder.append(String.format(level.substring(0, 1)))
      builder.append(Console.RESET)
      builder.toString()
    }

    override def undefined(entry: Log.Entry): Boolean = false

  }

  case object LocationUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "location", "loc"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      withMargin(entry.context.static.position.toString, margin)
    }

    override def undefined(entry: Log.Entry): Boolean = false

  }

  case object IdUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "id"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      withMargin(entry.context.static.id.id, margin)
    }

    override def undefined(entry: Log.Entry): Boolean = false

  }

  case object MessageUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "message", "msg"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      LogFormat.Default.formatMessage(entry, withColors).message
    }

    override def undefined(entry: Log.Entry): Boolean = false
  }

  case object CustomContextUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "custom-ctx", "custom"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      import entry.context.customContext.values
      val builder = new StringBuilder()

      if (values.nonEmpty) {
        val customContextString = values.map(v => LogFormat.Default.formatKv(withColors)(v.name, v.value)).mkString(", ")
        builder.append(s"{$customContextString}")
      }

      builder.toString()
    }

    override def undefined(entry: Log.Entry): Boolean = {
      entry.context.customContext.values.isEmpty
    }

  }

  def apply(alias: String): Option[LogUnit] = {
    all.get(alias)
  }

  private val all = Set(
    ThreadUnit
    , TimestampUnit
    , LevelUnit
    , IdUnit
    , LocationUnit
    , CustomContextUnit
    , MessageUnit
  ).flatMap {
    unit =>
      unit.aliases.map(_ -> unit)
  }.toMap


}

