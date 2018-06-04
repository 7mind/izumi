package com.github.pshirshov.izumi.logstage.api.rendering.logunits

import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.{ConsoleColors, RenderedMessage}

import scala.collection.mutable

case class Margin(elipsed: Boolean, size: Int)

sealed trait LogUnit {

  def aliases: Vector[String]

  def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String

  def undefined(entry: Log.Entry) : Boolean
}

object LogUnit {

  def withMargin(string: String, margin: Option[Margin]): String = {
    margin match {
      case Some(Margin(true, pad))  => string.ellipsedLeftPad(pad)
      case Some(Margin(_, pad)) => string.leftPad(pad)
      case None => string
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

      withMargin(builder.toString(),margin)

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
      withMargin(s"(${entry.context.static.file}:${entry.context.static.line})", None)

    }

    override def undefined(entry: Log.Entry): Boolean = false

  }

  case object MessageUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "message", "msg"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      formatMessage(entry, withColors).message
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
        val customContextString = values.map(formatKv(withColors)).mkString(", ")
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

  private val all = Set(ThreadUnit, TimestampUnit, LevelUnit, LocationUnit, CustomContextUnit, MessageUnit).flatMap {
    unit =>
      unit.aliases.map(_ -> unit)
  }.toMap


  private def formatKv(withColor: Boolean)(kv: (String, Any)): String = {
    if (withColor) {
      s"${Console.GREEN}${kv._1}${Console.RESET}=${Console.CYAN}${kv._2}${Console.RESET}"
    } else {
      s"${kv._1}=${kv._2}"
    }
  }

  def formatMessage(entry: Log.Entry, withColors: Boolean): RenderedMessage = {
    val templateBuilder = new StringBuilder()
    val messageBuilder = new StringBuilder()

    val head = entry.message.template.parts.head
    templateBuilder.append(StringContext.treatEscapes(head))
    messageBuilder.append(StringContext.treatEscapes(head))

    val balanced = entry.message.template.parts.tail.zip(entry.message.args)
    val unbalanced = entry.message.args.takeRight(entry.message.args.length - balanced.length)

    val argToStringColored: Any => String = argValue => argToString(argValue, withColors)

    val parameters = new mutable.HashMap[String, mutable.Set[String]] with mutable.MultiMap[String, String]

    balanced.foreach {
      case (part, (argName, argValue)) =>

        val (argNameToUse, partToUse) = if (part.startsWith(":") && part.length > 1) {
          val spaceIdx = part.indexOf(' ')

          val idx = if (spaceIdx > 0) {
            spaceIdx
          } else {
            part.length
          }

          (part.substring(1, idx), part.substring(idx))
        } else {
          (argName, part)
        }

        parameters.addBinding(argNameToUse, argValue.toString)

        templateBuilder.append('{')
        templateBuilder.append(argNameToUse)
        templateBuilder.append('}')
        templateBuilder.append(StringContext.treatEscapes(partToUse))

        messageBuilder.append(formatKv(withColors)((argNameToUse, argToStringColored(argValue))))
        messageBuilder.append(StringContext.treatEscapes(partToUse))
    }

    unbalanced.foreach {
      case (argName, argValue) =>
        templateBuilder.append("; ?")
        messageBuilder.append("; ")
        messageBuilder.append(formatKv(withColors)((argName, argToStringColored(argValue))))
    }

    RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString(), parameters.mapValues(_.toSet).toMap)
  }

  private def argToString(argValue: Any, withColors: Boolean): String = {
    argValue match {
      case e: Throwable =>
        if (withColors) {
          s"${Console.YELLOW}${e.toString}${Console.RESET}"
        } else {
          e.toString
        }

      case _ =>
        argValue.toString
    }
  }
}
