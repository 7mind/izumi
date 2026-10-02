package izumi.fundamentals.assertions

import scala.util.control.NonFatal

object AssertionRenderer {
  def render(diagnostic: AssertionDiagnostic, context: AssertionContext): RenderedAssertion = {
    val limits = context.limits
    val output = new BoundedText(limits.totalCharacters)
    val source = diagnostic.source
    val validation = validateSource(source, context.sourceProvider)
    val renderingFailures = Vector.newBuilder[RenderingFailure]
    output.append(source.identity.path)
    source.span match {
      case SourceSpan.Range(start, _) => output.append(s":${start.line + 1}:${start.column + 1}")
      case SourceSpan.Point(point) => output.append(s":${point.line + 1}:${point.column + 1} (range unavailable)")
      case SourceSpan.Unavailable => output.append(" (source position unavailable)")
    }
    output.append("\nAssertion failed\n")
    source.text match {
      case CompiledText.Available(text) =>
        val excerpt = bounded(text, limits.excerptCharacters)
        excerpt.split("\n", -1).foreach { line =>
          val expanded = expandTabs(line, limits.tabWidth, output.remaining)
          output.append(expanded)
          output.append("\n")
          source.span match {
            case _: SourceSpan.Range =>
              output.appendRepeated('^', math.max(1, visualWidth(expanded)))
              output.append("\n")
            case _ => ()
          }
        }
      case CompiledText.Unavailable => output.append("compiled expression text unavailable\n")
    }
    validation match {
      case SourceValidation.Mismatch => output.append("source mismatch; showing compiled excerpt\n")
      case SourceValidation.Unavailable => output.append("source unavailable; showing compiled excerpt\n")
      case SourceValidation.RangeUnavailable => output.append("source range unavailable; surrounding source not used\n")
      case SourceValidation.ProviderFailure(cause) => output.append(s"source provider failed: ${cause.getClass.getName}\n")
      case SourceValidation.Matching => ()
    }
    diagnostic.observations.take(limits.observations).zipWithIndex.foreach {
      case (observation, index) =>
        val site = observation.site
        site.span match {
          case SourceSpan.Range(start, _) => output.append(s"${start.line + 1}:${start.column + 1} ")
          case SourceSpan.Point(point) => output.append(s"${point.line + 1}:${point.column + 1} (range unavailable) ")
          case SourceSpan.Unavailable => output.append("(position unavailable) ")
        }
        site.text match {
          case CompiledText.Available(text) => output.append(bounded(text, limits.excerptCharacters))
          case CompiledText.Unavailable => output.append(site.kind.toString)
        }
        output.append(" = ")
        observation.evaluation match {
          case Evaluation.NotEvaluated => output.append("not evaluated")
          case Evaluation.Evaluated(value) =>
            try output.append(bounded(value.render(context.valueRenderer), limits.valueCharacters))
            catch {
              case NonFatal(cause) =>
                renderingFailures += RenderingFailure(index, cause)
                output.append(s"<value renderer failed: ${cause.getClass.getName}>")
            }
        }
        output.append("\n")
    }
    if (diagnostic.observations.size > limits.observations) output.append("additional observations omitted\n")
    RenderedAssertion(output.result(), validation, renderingFailures.result())
  }

  private def validateSource(source: ExpressionSource, provider: SourceProvider): SourceValidation = {
    try {
      provider.read(source.identity) match {
        case ProvidedSource.Unavailable => SourceValidation.Unavailable
        case ProvidedSource.Content(content) =>
          (source.span, source.text) match {
            case (SourceSpan.Range(start, end), CompiledText.Available(text)) =>
              if (start.offset >= 0 && end.offset >= start.offset && end.offset <= content.length && content.substring(start.offset, end.offset) == text) {
                SourceValidation.Matching
              } else SourceValidation.Mismatch
            case _ => SourceValidation.RangeUnavailable
          }
      }
    } catch {
      case NonFatal(cause) => SourceValidation.ProviderFailure(cause)
    }
  }

  private def bounded(value: String, limit: Int): String = {
    if (value.length <= limit) value else value.take(prefixLength(value, limit - 1)) + "…"
  }

  private def prefixLength(value: String, limit: Int): Int = {
    val end = math.min(value.length, limit)
    if (end > 0 && end < value.length && Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) end - 1
    else end
  }

  private def expandTabs(value: String, tabWidth: Int, limit: Int): String = {
    val output = new BoundedText(limit)
    var index = 0
    var column = 0
    while (index < value.length && output.remaining > 0) {
      val char = value.charAt(index)
      val codeUnits = if (Character.isHighSurrogate(char) && index + 1 < value.length && Character.isLowSurrogate(value.charAt(index + 1))) 2 else 1
      if (codeUnits > output.remaining) return output.result()
      if (char == '\t') {
        val width = tabWidth - column % tabWidth
        val boundedWidth = math.min(width, output.remaining)
        output.appendRepeated(' ', boundedWidth)
        column += boundedWidth
      } else {
        output.append(value.substring(index, index + codeUnits))
        column += 1
      }
      index += codeUnits
    }
    output.result()
  }

  private def visualWidth(value: String): Int = {
    var index = 0
    var width = 0
    while (index < value.length) {
      if (Character.isHighSurrogate(value.charAt(index)) && index + 1 < value.length && Character.isLowSurrogate(value.charAt(index + 1))) index += 1
      width += 1
      index += 1
    }
    width
  }

  private final class BoundedText(limit: Int) {
    private val output = new StringBuilder
    def remaining: Int = limit - output.length
    def append(text: String): Unit = {
      if (remaining > 0) output.append(text.take(prefixLength(text, remaining)))
      ()
    }
    def appendRepeated(character: Char, count: Int): Unit = {
      val boundedCount = math.min(count, remaining)
      var index = 0
      while (index < boundedCount) {
        output.append(character)
        index += 1
      }
    }
    def result(): String = output.result()
  }
}
