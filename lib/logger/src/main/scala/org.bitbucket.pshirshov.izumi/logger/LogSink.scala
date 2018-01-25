package org.bitbucket.pshirshov.izumi.logger

import java.time.LocalDateTime

import com.ratoshniuk.izumi.Log

trait LogSink {

  def parseValues(msg: Log.Message): Map[String, Any] = {

    def truncateKey(s: String) = {
      // TODO : support only when `=` after string (implement correct regex)
      "(\\w+)=".r.findFirstIn(s).map(_.dropRight(1))
    }

    msg.args.zip(msg.template.parts).map {
      case (v, k) => Option(k).flatMap(truncateKey).getOrElse(s"unknown_$v") -> v
    }.toMap
  }

  def renderMessage(e: Log.Entry, onlyTemplate: Boolean = false): String = {
    val parts = e.message.template.parts
    val args = e.message.args

    val customContext = e.context.customContext.values

    // TODO : macro fetching args names for full text search
    val builder = if (onlyTemplate) {
      parts.zip(args).map { case (k, arg) => k + s"{{$arg}}" }
    } else {
      (customContext.keys.map(_ + "=") ++ parts).zip(customContext.values.map(_ + ", ") ++ args)
        .map { case (k, arg) => k + arg }
    }
    builder.foldLeft("")(_ + _)
  }

  // here we may:
  // 0) Keep a message buffer
  // 3) Perform rendering/save values into database/etc


  // TODO : implement message buffer

  def flush(e: Log.Entry): Unit = {
    val renderedMessage = renderMessage(e)
    val fullValues = e.context.customContext.values ++
      parseValues(e.message) ++
      Map("message" -> renderedMessage, "message_template" -> renderMessage(e, onlyTemplate = true))

    val coloredLvl = s"${GUIUtils.logLevelColor(e.context.dynamic.level)}${e.context.dynamic.level}${Console.RESET}"
    val thread = s"${e.context.dynamic.threadData.threadId}:${e.context.dynamic.threadData.threadName}"
    val renderedSystemOutput = String.format(
      "%-25s%-16s%-8s%-30s", LocalDateTime.now(), coloredLvl, thread, e.context.static.id
    )
    println {s"""$renderedSystemOutput : $renderedMessage"""}
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
