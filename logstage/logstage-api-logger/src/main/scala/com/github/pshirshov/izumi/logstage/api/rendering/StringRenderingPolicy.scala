package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy.{Constant, LogMessageItem, WithMargin}
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.{LogUnit, Margin}

import scala.collection.mutable.ListBuffer

class StringRenderingPolicy(options: RenderingOptions, renderingLayout: Option[String] = None) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }

  private implicit val policyLayout: Iterable[LogMessageItem] = renderedLayout(renderingLayout.getOrElse(StringRenderingPolicy.defaultRendering))

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
    def parseLogUnit(chars: List[Char]): (List[Char], List[Char]) = {
      def traverseLogUnit(chars: List[Char], openBrackets: Int = 1, payload: List[Char] = List.empty): (List[Char], List[Char]) = {
        if (openBrackets == 0) {
          (chars, payload)
        } else {
          chars match {
            case item +: xs if item == '}' =>
              traverseLogUnit(xs, openBrackets - 1, payload)
            case item +: xs if item == '{' =>
              traverseLogUnit(xs, openBrackets + 1, payload)
            case item +: xs =>
              traverseLogUnit(xs, openBrackets, payload :+ item)
            case Nil if openBrackets > 0 => throw new IllegalArgumentException("found unclosed braces")
            case Nil => (Nil, payload)
          }
        }
      }

      val (remained, payload) = traverseLogUnit(chars)
      (remained, payload)
    }

    def traverseString(string: List[Char], buffer: List[LogMessageItem] = List.empty): List[LogMessageItem] = {
      string match {
        case ith +: jth +: others if ith == '$' && jth == '{' => {
          val (remained, maybeLogUnit) = parseLogUnit(others)
          traverseString(remained, buffer :+ parseLogUnitWithMargin(maybeLogUnit).getOrElse(Constant(s"$ith$jth" + maybeLogUnit + "}")))
        }
        case ith +: others =>
          traverseString(others, buffer :+ Constant(ith))
        case ith +: Nil =>
          buffer :+ Constant(ith)
        case Nil =>
          buffer
      }
    }

    val res = traverseString(pattern.toList)

    findDuplicateUnits(res).foreach {
      duplicate =>
        throw new IllegalArgumentException(s"Found duplicated log unit in rendering layout: ${duplicate.aliases.head}")
    }
    res
  }

  private def findDuplicateUnits(logItems: Iterable[LogMessageItem]): Option[LogUnit] = {
    val entries = scala.collection.mutable.HashSet.empty[LogUnit]
    logItems.collectFirst {
      case WithMargin(unit, _) if !entries.add(unit) => unit
    }
  }


  private def performRendering(e: Log.Entry, withColor: Boolean)(implicit builder: Iterable[LogMessageItem]): String = {

    val trimedBuilder = trimLogItems(builder).map(item => (item.unit, item.constants)).flatMap {
      case (Some(k), constants) if k.unit.undefined(e) => constants.dropWhile(_.isSpace)
      case (Some(key), constants) => key +: constants
      case (_, constants) => constants
    }

    trimedBuilder.foldLeft("")(_ + _.perform(e, withColor)) // @pshirshov this one or use string interpolator?
  }

  private def parseLogUnitWithMargin(chars: List[Char]): Option[WithMargin[_]] = {
    val splitter = chars.indexWhere(_ == '[')

    val (alias, marginPart) = if (splitter != -1) {
      chars.splitAt(splitter)
    } else {
      (chars, List.empty)
    }

    LogUnit.apply(alias.mkString("")).map {
      unit =>
        val maybeMargin = marginPart match {
          case '[' +: payload :+ ']' =>
            val trimmed = payload.filterNot(_.isSpaceChar)
            trimmed match {
              case digits if digits.forall(_.isDigit) =>
                Some(Margin(elipsed = false, digits.mkString("").toInt))
              case '.' +: '.' +: digits if digits.forall(_.isDigit) =>
                Some(Margin(elipsed = true, digits.mkString("").toInt))
              case _ =>
                throw new IllegalArgumentException("Unexpected margin format")
            }
          case Nil =>
            None
          case _ =>
            throw new IllegalArgumentException("Unexpected margin format")
        }
        WithMargin(unit, maybeMargin)
    }
  }

  private def trimLogItems(builder: Iterable[LogMessageItem]): Vector[WithConstants[_ <: LogUnit]] = {
    builder.toSeq.foldLeft(ListBuffer.empty[WithConstants[_ <: LogUnit]]) {
      case (acc, logUnit: WithMargin[_]) =>
        acc += WithConstants(Some(logUnit))
      case (acc, constant: Constant) =>
        if (acc.isEmpty) {
          val item = WithConstants()
          acc += item
        }
        acc.last.append(constant)
        acc
    }
  }.toVector
}

case class WithConstants[T <: LogUnit](unit: Option[WithMargin[T]] = None) {

  private val xs = ListBuffer.empty[Constant]

  def append(cons: Constant*): Unit = xs ++= cons

  def constants: Vector[LogMessageItem] = xs.toVector
}


object StringRenderingPolicy {

  val defaultRendering = "ssss${level} ${ts} ${thread} ${location} ${custom-ctx} ${msg}"

  sealed trait LogMessageItem {
    def perform(e: Log.Entry, withColor: Boolean): String

    def isSpace: Boolean
  }

  object LogMessageItem {
    val space = " "

  }

  case class Constant(i: String) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = i.toString

    override def isSpace: Boolean = i.contains(LogMessageItem.space)
  }

  object Constant {
    def apply(i: Char): Constant = new Constant(i.toString)
  }

  case class WithMargin[T <: LogUnit](unit: LogUnit, margin: Option[Margin]) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = unit.renderUnit(e, withColor, margin)

    override val isSpace: Boolean = false
  }


}
