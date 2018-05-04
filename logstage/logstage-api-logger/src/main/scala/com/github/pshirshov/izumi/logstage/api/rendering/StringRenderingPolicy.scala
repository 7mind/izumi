package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment
import java.util.regex.{Matcher, Pattern}

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy.{Constant, Form, LogMessageItem}
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

class StringRenderingPolicy(options: RenderingOptions, layout: Option[String] = None) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }

  private implicit val policyLayout: Iterable[LogMessageItem] = renderedLayout(layout.getOrElse("${level} ${ts}\t\t${thread}\t${location}${custom-ctx}${msg}"))

  override def render(entry: Log.Entry): String = {
    val sb = new StringBuffer(performRendering(entry, withColors))
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

  private def renderedLayout(pattern: String): Iterable[LogMessageItem] = {
    val buffer = ListBuffer.empty[LogMessageItem]
    var i = 0
    val charList = pattern.toList

    while (i < charList.length) {
      val charIth = charList(i)
      val charIthNext = charList(i + 1)
      if (charIth == '$') {
        if (charIthNext == '{') {
          var j = i + 2
          var closeableRemained = 1
          val buffer_i = ListBuffer.empty[Constant[_]]
          while (closeableRemained > 0) {
            val curChar = charList(j)
            if (curChar == '}') {
              closeableRemained -= 1
            } else {
              buffer_i += Constant(curChar)
            }
            j += 1
          }
          val end = j

          val str = buffer_i.map(_.i).mkString("")
          buffer += {
            LogUnit.apply(str) match {
              case Some(unit) =>
                Form(unit)
              case None =>
                Constant(s"$charIth$charIthNext" + str + "}")
            }
          }
          i = end
        } else {
          buffer += Constant(charIthNext)
          i += 1
        }
      } else {
        buffer += Constant(charIth)
        i += 1
      }
    }
    buffer
  }

  private def performRendering(e: Log.Entry, withColor: Boolean)(implicit builder: Iterable[LogMessageItem]): String = {
    builder
      .map(_.perform(e, withColor))
      .mkString("")
  }
}


object StringRenderingPolicy {

  sealed trait LogMessageItem {
    def perform(e: Log.Entry, withColor: Boolean): String
  }

  case class Constant[T](i: T) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = i.toString
  }

  case class Form(unit: LogUnit) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = unit.renderUnit(e, withColor)
  }

}

/**
  * if (curChar == '{') {
  * //            closeableRemained += 1
  * //          } else if (curChar == '}') {
  * //            closeableRemained -=1
  * //          } else {
  * //            buffer_i += Char(curChar.toString)
  * //          }
  *
  **/