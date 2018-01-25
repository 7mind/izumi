package org.bitbucket.pshirshov.izumi.logger

import java.time.LocalDateTime

trait LogSink {

  def parseValues(msg: Log.Message): Map[String, Any] = {
    def truncateKey(s: String) = {
      "(\\w+)=".r.findFirstIn(s).map(_.dropRight(1))
    }

    msg.args.toList.zip(msg.template.parts.toList).map {
      case (v, k) =>
        Option(k).flatMap(truncateKey).getOrElse(s"unknown_$v") -> v
    }.toMap
  }

  def renderMessage(e: Log.Entry, template: Boolean = false): String = {
    val parts = e.message.template.parts.toList
    val args = e.message.args.toList

    val builder = if (template) {
      // TODO : macro fetching args names for full text search
      parts.zip(args).map { case (k, arg) => k + s"{{$arg}}" }
    } else {
      (e.context.customContext.values.keys.toList.map(_ + "=") ++ parts)
        .zip(e.context.customContext.values.values.toList.map(_ + ", ") ++ args)
        .map { case (k, arg) => k + arg }
    }
    builder.foldLeft("")(_ + _)
  }

  // here we may:
  // 0) Keep a message buffer
  // 1) Parse log message into values=Map[String, Any], message_template=parts.join('_'), message
  // 2) Make full_values = custom_log_context + context + values + Map(message-> message, message_template->message_template)
  // 3) Perform rendering/save values into database/etc


  // TODO : implement message buffer

  def flush(e: Log.Entry): Unit = {

    val renderedMessage = renderMessage(e)

    val fullValues = e.context.customContext.values ++
      parseValues(e.message) ++
      Map("message" -> renderedMessage, "message_template" -> renderMessage(e, template = true))

    val lvl = e.context.dynamic.level
    val coloredLvl = s"${GUIUtils.logLevelColor(lvl)}$lvl${Console.RESET}"

    val timeStamp = LocalDateTime.now()
    val thread = s"${e.context.dynamic.threadData.threadId}:${e.context.dynamic.threadData.threadName}"
    val className = e.context.static.id

    val renderedSystemOutput = String.format(
      "%-25s%-16s%-8s%-30s", timeStamp, coloredLvl, thread, className
    )
    println {
      s"""$renderedSystemOutput : ${renderedMessage}"""
    }
  }
}


object GUIUtils {
  def logLevelColor(lvl: Log.Level) = lvl match {
    case Log.Level.Info => Console.GREEN
    case Log.Level.Debug => Console.BLUE
    case Log.Level.Warn => Console.YELLOW
    case Log.Level.Error => Console.RED
  }
}
