package izumi.logstage.api.rendering.logunits

import izumi.fundamentals.platform.exceptions.IzThrowable
import izumi.logstage.api.Log
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.{LogstageCodec, RenderedMessage, RenderedParameter, RenderingOptions}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait LogFormat {
  @inline def formatMessage(entry: Log.Entry, options: RenderingOptions): RenderedMessage

  @inline def formatArg(arg: LogArg, withColors: Boolean): RenderedParameter
  @inline def formatKv(withColor: Boolean)(name: String, codec: Option[LogstageCodec[Any]], value: Any): String
}

object LogFormat {

  abstract class LogFormatImpl extends LogFormat {
    def formatArg(arg: LogArg, withColors: Boolean): RenderedParameter = {
      RenderedParameter(arg, argToString(arg.codec, arg.value, withColors), normalizeName(arg.name))
    }

    def formatKv(withColor: Boolean)(name: String, codec: Option[LogstageCodec[Any]], value: Any): String = {
      val key = wrapped(withColor, Console.GREEN, name)
      val v = argToString(codec, value, withColor)
      s"$key=$v"
    }

    def formatMessage(entry: Log.Entry, options: RenderingOptions): RenderedMessage = {
      val withColors = options.colored
      val hideKeys = options.hideKeys
      val templateBuilder = new StringBuilder()
      val messageBuilder = new StringBuilder()

      // here we fix representation of "malformed" strings like arg1 + arg2 + arg3.
      // While technically it IS correct to print all the args concatenated, it makes sense to separate them
      val staticParts = entry.message.template.parts
        .foldLeft((Option.empty[String], Seq.empty[String])) {
          case ((last, acc), part) =>
            if (part == "" && last.contains("")) {
              (Some(part), acc.init ++ Seq(" ", " "))
            } else {
              (Some(part), acc :+ part)
            }
        }._2

      val head = staticParts.head
      templateBuilder.append(handle(head))
      messageBuilder.append(handle(head))

      val balanced = staticParts.tail.zip(entry.message.args)

      val occurences = mutable.HashMap[String, Int]()

      val parameters = mutable.ArrayBuffer[RenderedParameter]()
      process(occurences, templateBuilder, messageBuilder, parameters, withColors, hideKeys)(balanced)

      val unbalancedArgs = mutable.ArrayBuffer[RenderedParameter]()
      val unbalanced = entry.message.args.takeRight(entry.message.args.length - balanced.length)
      processUnbalanced(occurences, withColors, hideKeys, templateBuilder, messageBuilder, unbalancedArgs, unbalanced)

      if (options.withExceptions) {
        messageBuilder.append(traceThrowables(options, entry))
      }

      RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString(), parameters.toSeq, unbalancedArgs.toSeq)
    }

    def traceThrowables(options: RenderingOptions, entry: Log.Entry): String = {
      import izumi.fundamentals.platform.exceptions.IzThrowable.*

      val throwables = entry.throwables
      if (throwables.nonEmpty) {
        throwables.zipWithIndex
          .map {
            case (t, idx) =>
              val builder = new StringBuilder
              if (options.colored) {
                builder.append(Console.YELLOW)
                builder.append("💔 ")
              }
              if (throwables.size > 1) {
                builder.append(s"Exception #$idx:\n")
              }

              // TODO: we may try to use codec here
              builder.append(t.value.stacktraceString)
              if (options.colored) {
                builder.append(Console.RESET)
              }
              builder.toString()
          }
          .mkString("\n", "\n", "")
      } else {
        ""
      }
    }

    @inline private def processUnbalanced(
      occurences: mutable.HashMap[String, Int],
      withColors: Boolean,
      hideKeys: Boolean,
      templateBuilder: StringBuilder,
      messageBuilder: StringBuilder,
      unbalancedArgs: ArrayBuffer[RenderedParameter],
      unbalanced: Seq[LogArg],
    ) = {
      if (unbalanced.nonEmpty) {
        templateBuilder.append(" {{ ")
        messageBuilder.append(" {{ ")

        val parts = List.fill(unbalanced.size - 1)("; ") :+ ""

        val x = parts.zip(unbalanced)
        process(occurences, templateBuilder, messageBuilder, unbalancedArgs, withColors, hideKeys)(x)

        templateBuilder.append(" }}")
        messageBuilder.append(" }}")
      }
    }

    @inline private def process(
      occurences: mutable.HashMap[String, Int],
      templateBuilder: mutable.StringBuilder,
      messageBuilder: mutable.StringBuilder,
      acc: mutable.ArrayBuffer[RenderedParameter],
      withColors: Boolean,
      hideKeys: Boolean,
    )(balanced: collection.Seq[(String, LogArg)]
    ): Unit = {
      balanced.foreach {
        case (part, arg) =>
          val uncoloredRepr = formatArg(arg, withColors = false)

          acc += uncoloredRepr

          val rawOriginalName = uncoloredRepr.arg.name
          val rawNormalizedName = uncoloredRepr.normalizedName

          val count = occurences.getOrElseUpdate(rawOriginalName, 0)
          occurences.put(rawOriginalName, count + 1)

          val (normalizedName, originalName) = if (count == 0) {
            (rawNormalizedName, rawOriginalName)
          } else {
            (s"$rawNormalizedName.$count", s"$rawOriginalName.$count")
          }

          val visibleName = if (withColors) {
            originalName
          } else {
            normalizedName
          }

          templateBuilder.append("${")
          templateBuilder.append(normalizedName)
          templateBuilder.append('}')
          templateBuilder.append(handle(part))

          val maybeColoredRepr = if (withColors) {
            argToString(arg.codec, uncoloredRepr.arg.value, withColors)
          } else {
            uncoloredRepr.repr
          }

          if (!hideKeys && !uncoloredRepr.arg.hiddenName) {
            messageBuilder.append(formatKvStrings(withColors, visibleName, maybeColoredRepr))
          } else {
            messageBuilder.append(maybeColoredRepr)
          }
          messageBuilder.append(handle(part))
      }
    }

    @inline private def normalizeName(s: String): String = {
      if (s.forall(_.isUpper) || s.startsWith("UNNAMED:") || s.startsWith("EXPRESSION:")) {
        s
      } else {
        import izumi.fundamentals.platform.strings.IzString.*
        s.replace(' ', '_').camelToUnderscores
      }
    }

    @inline private def handle(part: String): String = {
      StringContext.processEscapes(part)
    }

    @inline private def formatKvStrings(withColor: Boolean, name: String, value: String): String = {
      val key = wrapped(withColor, Console.GREEN, name)
      val v = wrapped(withColor, Console.CYAN, value)
      s"$key=$v"
    }

    @inline private def argToString(codec: Option[LogstageCodec[Any]], argValue: Any, withColors: Boolean): String = {
      argValue match {
        case null =>
          wrapped(withColors, Console.YELLOW, "null")

        case e: Throwable =>
          // TODO: we may try to use codec here
          wrapped(withColors, Console.YELLOW, e.toString)

        case _ =>
          try {
            codec match {
              case Some(codec) =>
                val writer = codec.makeReprWriter(withColors)
                codec.write(writer, argValue)
                wrapped(withColors, Console.CYAN, writer.translate())
              case None =>
                wrapped(withColors, Console.CYAN, toString(argValue))

            }
          } catch {
            case f: Throwable =>
              import IzThrowable.*
              val message = s"[${argValue.getClass.getName}#toString failed]\n${f.stacktraceString} "
              wrapped(withColors, Console.RED, message)
          }
      }
    }

    protected def toString(argValue: Any): String

    @inline private def wrapped(withColors: Boolean, color: String, message: String): String = {
      if (withColors) {
        s"$color$message${Console.RESET}"
      } else {
        message
      }
    }

  }

  object Default extends LogFormatImpl {

    protected def toString(argValue: Any): String = {
      argValue match {
        case o => o.toString
      }

    }
  }

}
