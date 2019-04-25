package com.github.pshirshov.izumi.logstage.api.rendering.logunits

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogArg
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderedMessage, RenderedParameter}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

object LogFormat {
  def formatArg(arg: LogArg, withColors: Boolean): RenderedParameter = {
    RenderedParameter(arg, argToString(arg.value, withColors), normalizeName(arg.name))
  }

  def formatMessage(entry: Log.Entry, withColors: Boolean): RenderedMessage = {
    val templateBuilder = new StringBuilder()
    val messageBuilder = new StringBuilder()

    val staticParts = entry.message.template.parts
    val head = staticParts.head
    templateBuilder.append(handle(head))
    messageBuilder.append(handle(head))

    val balanced = staticParts.tail.zip(entry.message.args)

    val occurences = mutable.HashMap[String, Int]()

    val parameters = mutable.ArrayBuffer[RenderedParameter]()
    process(occurences, templateBuilder, messageBuilder, parameters, withColors)(balanced)

    val unbalancedArgs = mutable.ArrayBuffer[RenderedParameter]()
    val unbalanced = entry.message.args.takeRight(entry.message.args.length - balanced.length)
    processUnbalanced(occurences, withColors, templateBuilder, messageBuilder, unbalancedArgs, unbalanced)

    RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString(), parameters, unbalancedArgs)
  }

  @inline protected[logunits] def formatKv(withColor: Boolean)(name: String, value: Any): String = {
    val key = wrapped(withColor, Console.GREEN, name)
    val v = argToString(value, withColor)
    s"$key=$v"
  }

  @inline private[this] def processUnbalanced(occurences: mutable.HashMap[String, Int], withColors: Boolean, templateBuilder: StringBuilder, messageBuilder: StringBuilder, unbalancedArgs: ArrayBuffer[RenderedParameter], unbalanced: Seq[LogArg]) = {
    if (unbalanced.nonEmpty) {
      templateBuilder.append(" {{ ")
      messageBuilder.append(" {{ ")

      val parts = List.fill(unbalanced.size - 1)("; ") :+ ""

      val x = parts.zip(unbalanced)
      process(occurences, templateBuilder, messageBuilder, unbalancedArgs, withColors)(x)

      templateBuilder.append(" }}")
      messageBuilder.append(" }}")
    }
  }

  @inline private[this] def process(occurences: mutable.HashMap[String, Int], templateBuilder: mutable.StringBuilder, messageBuilder: mutable.StringBuilder, acc: mutable.ArrayBuffer[RenderedParameter], withColors: Boolean)(balanced: Seq[(String, LogArg)]): Unit = {
    balanced.foreach {
      case (part, arg) =>
        val uncoloredRepr = formatArg(arg, withColors = false)

        acc += uncoloredRepr

        val count = occurences.getOrElseUpdate(uncoloredRepr.arg.name, 0)
        occurences.put(uncoloredRepr.arg.name, count + 1)
        val (visibleName, templateName) = if (count == 0) {
          (uncoloredRepr.visibleName, uncoloredRepr.arg.name)
        } else {
          (s"${uncoloredRepr.visibleName}.$count", s"${uncoloredRepr.arg.name}.$count")
        }

        templateBuilder.append("${")
        templateBuilder.append(templateName)
        templateBuilder.append('}')
        templateBuilder.append(handle(part))

        val maybeColoredRepr = if (withColors) {
          argToString(uncoloredRepr.arg.value, withColors)
        } else {
          uncoloredRepr.repr
        }

        if (!uncoloredRepr.arg.hiddenName) {
          messageBuilder.append(formatKv(withColors)(visibleName, maybeColoredRepr))
        } else {
          messageBuilder.append(maybeColoredRepr)
        }
        messageBuilder.append(handle(part))
    }
  }

  @inline private[this] def normalizeName(s: String): String = {
    if (s.forall(_.isUpper) || s.startsWith("UNNAMED:") || s.startsWith("EXPRESSION:")) {
      s
    } else {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      s.replace(' ', '_').camelToUnderscores
    }
  }

  @inline private[this] def handle(part: String) = {
    StringContext.treatEscapes(part)
  }

  @inline private[this] def argToString(argValue: Any, withColors: Boolean): String = {
    argValue match {
      case null =>
        wrapped(withColors, Console.YELLOW, "null")

      case e: Throwable =>
        wrapped(withColors, Console.YELLOW, e.toString)

      case _ =>
        Try(argValue.toString) match {
          case Success(s) =>
            wrapped(withColors, Console.CYAN, s)

          case Failure(f) =>
            import IzThrowable._
            val message = s"[${argValue.getClass.getName}#toString failed]\n${f.stackTrace} "
            wrapped(withColors, Console.RED, message)
        }
    }
  }

  @inline private[this] def wrapped(withColors: Boolean, color: String, message: String): String = {
    if (withColors) {
      s"$color$message${Console.RESET}"
    } else {
      message
    }
  }
}
