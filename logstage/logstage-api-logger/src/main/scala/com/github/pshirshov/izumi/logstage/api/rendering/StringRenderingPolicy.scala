package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment
import java.util.regex.{Matcher, Pattern}

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy._
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit

import scala.util.matching.Regex

class StringRenderingPolicy(options: RenderingOptions, layout: Option[String] = None) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }

  private val policyLayout = layout.getOrElse("${level[:..1]} ${ts}\t\t${thread}\t${location}${custom-ctx}${msg}")

  override def render(entry: Log.Entry): String = {
    val sb = new StringBuffer()
    val logUnitFinder = logUnitMatcher(policyLayout)

    val mutableSet = scala.collection.mutable.HashSet.empty[LogUnit]

    while (logUnitFinder.find()) {
      StringRenderingPolicy.logUnitLabel findAllMatchIn logUnitFinder.group(1) foreach {
        i =>
          LogUnit.apply(i.group(1)).foreach {
            logUnit =>
              if (mutableSet.add(logUnit)) {
                logUnitFinder.appendReplacement(sb, logUnit.renderUnit(entry, withColors))
              } else {
                throw new IllegalArgumentException(s"log unit must be at once. Conflicts : ${logUnit.aliases.mkString(",")}")
              }
          }
      }
    }

    logUnitFinder.appendTail(sb)
    if (options.withExceptions) {
      sb.append(traceThrowable(entry))
    }
    sb.toString

  }


  def traceThrowable(entry: Log.Entry): String = {
    entry.firstThrowable match {
      case Some(t) =>
        val builder = new StringBuilder
        builder.append('\n')
        if (withColors) {
          builder.append(Console.YELLOW)
        }
        import IzThrowable._
        builder.append(t.stackTrace)
        if (withColors) {
          builder.append(Console.RESET)
        }
        builder.toString()
      case None =>
        ""
    }
  }
}


object StringRenderingPolicy {
  val logUnitMatcher: String => Matcher = Pattern.compile("(\\$\\{[^}]+\\})").matcher(_: String)
  val logUnitLabel = new Regex("\\{(.*?)\\}")
}
