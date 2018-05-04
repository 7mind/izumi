package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy.{Constant, Form, LogMessageItem}
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.LogUnit

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class StringRenderingPolicy(options: RenderingOptions, renderingLayout: Option[String] = None) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }

  private implicit val policyLayout: Iterable[LogMessageItem] = renderedLayout(renderingLayout.getOrElse("${level} ${ts}\t\t${thread}\t${location}${custom-ctx}${msg}"))

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

  // todo : tail rec
  // todo : decompose to small functions
  // todo : maybe polish notation we need to use for checking braces and theirs closing
  // todo : inner props i.e. margins

  private def renderedLayout(pattern: String): Iterable[LogMessageItem] = {

    def traverseString(string: List[Char], buffer: List[LogMessageItem] = List.empty): List[LogMessageItem] = {

      string match {
        case ith +: jth +: others if ith == '$' && jth == '{' => {
          var j = 0
          var closeableRemained = 1
          val buffer_i = ListBuffer.empty[Constant[_]]
          while (closeableRemained > 0) {
            val curChar = others(j)
            if (curChar == '}') {
              closeableRemained -= 1
            } else if (curChar == '{') {
              closeableRemained += 1
            } else {
              buffer_i += Constant(curChar)
            }
            j += 1
          }
          val str = buffer_i.map(_.i).mkString("")
          traverseString(others.zipWithIndex.dropWhile(_._2 < j).map(_._1), buffer :+ {
            LogUnit.apply(str) match {
              case Some(unit) => Form(unit)
              case None => Constant(s"$ith$jth" + str + "}")
            }
          })
        }
        case ith +: others => {
          traverseString(others, buffer :+ Constant(ith))
        }
        case ith +: Nil =>
          buffer :+ Constant(ith)
        case Nil =>
          buffer
      }
    }

    Try {
      traverseString(pattern.toList)
    } match {
      case Failure(_: IndexOutOfBoundsException) =>
        throw new IllegalArgumentException("found unclosed braces")
      case Success(items) =>
        findDuplicateUnits(items).foreach {
          duplicate =>
            throw new IllegalArgumentException(s"Found duplicated log unit in rendering layout: ${duplicate.aliases.head}")
        }
        println(items)
        items
    }
  }

  private def findDuplicateUnits(logItems: Iterable[LogMessageItem]): Option[LogUnit] = {
    val entries = scala.collection.mutable.HashSet.empty[LogUnit]
    logItems.collectFirst {
      case Form(unit) if !entries.add(unit) => unit
    }
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