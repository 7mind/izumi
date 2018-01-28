package com.github.pshirshov.izumi.logstage.api.rendering

import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.fundamentals.strings.IzString._

class StringRenderingPolicy(withColors: Boolean) extends RenderingPolicy {
  override def render(entry: Log.Entry): String = {
    val builder = new StringBuilder

    val fullValues = entry.context.customContext.values

    val level = entry.context.dynamic.level.toString.leftPad(5, ' ')
    val coloredLvl = if (withColors) {
      s"${ConsoleColors.logLevelColor(entry.context.dynamic.level)}$level${Console.RESET}"
    } else {
      level
    }

    val ts = {
      import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
      Instant.ofEpochMilli(entry.context.dynamic.tsMillis).atZone(ZoneId.systemDefault()).isoFormat
    }
    builder.append(ts)
    builder.append(' ')

    builder.append(coloredLvl)
    builder.append(' ')

    val location = s"${entry.context.static.file}:${entry.context.static.line}"
    builder.append('(')
    builder.append(location.ellipsedLeftPad(32))
    builder.append(") ")

    val threadName = entry.context.dynamic.threadData.threadName
    builder.append('{')
    builder.append(threadName.ellipsedLeftPad(15))
    builder.append("} ")


    builder.append(RenderingService.render(entry).message)
    builder.toString()
  }


}


