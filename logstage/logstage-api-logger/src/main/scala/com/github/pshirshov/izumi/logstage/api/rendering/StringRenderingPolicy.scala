package com.github.pshirshov.izumi.logstage.api.rendering

import java.awt.GraphicsEnvironment

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy.{Constant, LogMessageItem, WithMargin}
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.{LogUnit, Margin}

class StringRenderingPolicy(options: RenderingOptions, renderingLayout: Option[String] = None) extends RenderingPolicy {
  protected val withColors: Boolean = {
    (
      options.withColors &&
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true)
      ) &&
      !GraphicsEnvironment.isHeadless
  }

  private implicit val policyLayout: Iterable[LogMessageItem] = renderedLayout(renderingLayout.getOrElse("${level[2]} ${ts[..20]} ${thread}\t${location}${custom-ctx}${msg}"))

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

  // todo : maybe polish notation we need to use for checking braces and theirs closing
  // todo : inner props i.e. margins

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

    case class MaybeLogUnit(alias: String, margin: Option[String])

    def traverseString(string: List[Char], buffer: List[LogMessageItem] = List.empty): List[LogMessageItem] = {
      string match {
        case ith +: jth +: others if ith == '$' && jth == '{' => {
          val (remained, maybeLogUnit) = parseLogUnit(others)
          traverseString(remained,
            buffer :+ parseLogUnitWithMargin(maybeLogUnit).getOrElse {
              Constant(s"$ith$jth" + maybeLogUnit + "}")
            }
          )
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
    builder
      .map(_.perform(e, withColor))
      .mkString("")
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
}


object StringRenderingPolicy {

  sealed trait LogMessageItem {
    def perform(e: Log.Entry, withColor: Boolean): String
  }

  case class Constant[T](i: T) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = i.toString
  }

  case class WithMargin[T <: LogUnit](unit: LogUnit, margin: Option[Margin]) extends LogMessageItem {
    override def perform(e: Log.Entry, withColor: Boolean): String = unit.renderUnit(e, withColor, margin)
  }

}
