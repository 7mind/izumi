package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment
import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.logstage.api.logger.{RenderingOptions, RenderingPolicy}
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._


class StringRenderingPolicy(options: RenderingOptions) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("iz.log.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }


  override def render(entry: Log.Entry): String = {
    val builder = new StringBuilder


    val context = entry.context

    if (withColors) {
      builder.append(ConsoleColors.logLevelColor(context.dynamic.level))
      builder.append(Console.UNDERLINED)
      builder.append(Console.BOLD)
    }

    val level = context.dynamic.level.toString.substring(0, 1)
    builder.append(level)
    builder.append(' ')

    val ts = {
      import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
      Instant.ofEpochMilli(context.dynamic.tsMillis).atZone(ZoneId.systemDefault()).isoFormat
    }
    builder.append(ts)
    if (withColors) {
      builder.append(Console.RESET)
    }

    builder.append(' ')

    val location = s"(${context.static.file}:${context.static.line}) "
    builder.append(location.leftPad(32))

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
      builder.append("] ")
    }


    if (context.customContext.values.nonEmpty) {
      val customContextString = context.customContext.values.map(formatKv).mkString(", ")
      builder.append('{')
      builder.append(customContextString)
      builder.append("} ")
    }


    builder.append(formatMessage(entry).message)
    if (options.withExceptions) {
      entry.firstThrowable match {
        case Some(t) =>
          builder.append('\n')
          if (withColors) {
            builder.append(Console.YELLOW)
          }

          import IzThrowable._
          builder.append(t.stackTrace)

          if (withColors) {
            builder.append(Console.RESET)
          }
        case None =>
      }
    }

    builder.toString()
  }

  protected def formatKv(kv: (String, Any)): String = {
    if (withColors) {
      s"${Console.GREEN}${kv._1}${Console.RESET}=${Console.CYAN}${kv._2}${Console.RESET}"
    } else {
      s"${kv._1}=${kv._2}"
    }
  }

  def formatMessage(entry: Log.Entry): RenderedMessage = {
    val templateBuilder = new StringBuilder()
    val messageBuilder = new StringBuilder()
//    val rawMessageBuilder = new StringBuilder()

    val head = entry.message.template.parts.head
    templateBuilder.append(head)
    messageBuilder.append(head)
//    rawMessageBuilder.append(head)

    val balanced = entry.message.template.parts.tail.zip(entry.message.args)
    val unbalanced = entry.message.args.takeRight(entry.message.args.length - balanced.length)
    balanced.foreach {
      case (part, (argName, argValue)) =>
        templateBuilder.append('{')
        templateBuilder.append(argName)
        templateBuilder.append('}')
        templateBuilder.append(part)

        messageBuilder.append(formatKv((argName, argToString(argValue))))
        messageBuilder.append(part)

//        rawMessageBuilder.append('{')
//        rawMessageBuilder.append(argName)
//        rawMessageBuilder.append('=')
//        rawMessageBuilder.append(argToString(argValue))
//        rawMessageBuilder.append('}')

    }
    RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString())
  }


  protected def argToString(argValue: Any): String = {
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


