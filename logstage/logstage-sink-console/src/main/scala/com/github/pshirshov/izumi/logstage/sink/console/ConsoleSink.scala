package com.github.pshirshov.izumi.logstage.sink.console

import java.time.LocalDateTime

import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.Log.Message

class ConsoleSink extends LogSink {
  // TODO : implement message buffer
  def flush(e: Log.Entry): Unit = {
    val renderedMessage = renderMessage(e)
    val fullValues = e.context.customContext.values ++
      parseValues(e.message) ++
      Map("message" -> renderedMessage, "message_template" -> renderMessage(e, onlyTemplate = true))

    val coloredLvl = s"${GUIUtils.logLevelColor(e.context.dynamic.level)}${e.context.dynamic.level}${Console.RESET}"
    val thread = s"${e.context.dynamic.threadData.threadId}:${e.context.dynamic.threadData.threadName}"
    val renderedSystemOutput = String.format(
      "%-25s%-16s%-8s%-30s", LocalDateTime.now(), coloredLvl, thread, e.context.static.id.id
    )
    println {s"""$renderedSystemOutput : $renderedMessage"""}
  }

  def parseValues(msg: Message): Map[String, Any] = {
    msg.args.foldLeft(Map.empty[String, Any]) {
      case (map, (k, v)) =>
        map ++ Map(k -> v)
    }
  }

  def renderMessage(e: Log.Entry, onlyTemplate: Boolean = false): String = {
    val parts = e.message.template.parts
    val args = e.message.args

    val customContext = e.context.customContext.values


    if (onlyTemplate) {
      parts.zip(args).map {
        case (p, (id, value)) =>
          p + (if (id.startsWith("unnamed")) {
            s"$${${value}}"
          } else {
            s"$${${id}}"
          })
      }.foldLeft("")(_ + _)
    } else {
      // TODO : improve this one
      customContext.foldLeft("") {
        case (acc, (k, v)) => {
          acc + k + "=" + v
        }
      } + " " + parts.zip(args).map {
        case (p, (id, v)) => p + id + "=" + v
      }.foldLeft("")(_ + _)
    }
  }

}
