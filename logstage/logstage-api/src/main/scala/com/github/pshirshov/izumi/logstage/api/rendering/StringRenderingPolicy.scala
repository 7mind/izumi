package com.github.pshirshov.izumi.logstage.api.rendering

import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.fundamentals.strings.IzString._

class StringRenderingPolicy(withColors: Boolean) extends RenderingPolicy {
  override def render(entry: Log.Entry): String = {
    val builder = new StringBuilder

    val context = entry.context

    val level = context.dynamic.level.toString.leftPad(5, ' ')
    val coloredLvl = if (withColors) {
      s"${ConsoleColors.logLevelColor(context.dynamic.level)}$level${Console.RESET}"
    } else {
      level
    }

    val ts = {
      import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
      Instant.ofEpochMilli(context.dynamic.tsMillis).atZone(ZoneId.systemDefault()).isoFormat
    }
    builder.append(ts)
    builder.append(' ')

    builder.append(coloredLvl)
    builder.append(' ')

    val location = s"${context.static.file}:${context.static.line}"
    builder.append('(')
    builder.append(location.leftPad(32, ' '))
    builder.append(") ")

    val threadName = s"${context.dynamic.threadData.threadName}:${context.dynamic.threadData.threadId}"
    builder.append('[')
    builder.append(threadName.ellipsedLeftPad(15))
    builder.append("] ")

    if (context.customContext.values.nonEmpty) {
      val customContextString = context.customContext.values.map { case (k, v) => s"$k=$v" }.mkString(", ")
      builder.append('{')
      builder.append(customContextString)
      builder.append("} ")
    }


    builder.append(RenderingService.render(entry).message)
    builder.toString()
  }


}


