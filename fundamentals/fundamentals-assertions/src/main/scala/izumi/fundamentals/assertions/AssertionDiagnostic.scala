package izumi.fundamentals.assertions

sealed trait SourceRoot
object SourceRoot {
  case object Unspecified extends SourceRoot
  final case class Directory(path: String) extends SourceRoot {
    require(path.nonEmpty, "Empty source root")
  }
}

sealed trait SourceIdentity { def path: String }
object SourceIdentity {
  final case class Relative(path: String) extends SourceIdentity
  final case class Absolute(path: String) extends SourceIdentity
  final case class Virtual(path: String) extends SourceIdentity

  def recorded(path: String, virtual: Boolean, root: SourceRoot): SourceIdentity = {
    if (virtual) Virtual(path)
    else {
      val normalized = normalize(path)
      root match {
        case SourceRoot.Directory(directory) =>
          val normalizedRoot = normalize(directory)
          val prefix = normalizedRoot.stripSuffix("/") + "/"
          if (isAbsolute(normalized) == isAbsolute(normalizedRoot) && normalized.startsWith(prefix)) Relative(normalized.substring(prefix.length))
          else physical(normalized)
        case SourceRoot.Unspecified => physical(normalized)
      }
    }
  }

  private def physical(path: String): SourceIdentity = {
    if (isAbsolute(path)) Absolute(path)
    else Relative(path)
  }

  private def isAbsolute(path: String): Boolean = path.startsWith("/") || (hasDrivePrefix(path) && path.length >= 3 && path.charAt(2) == '/')

  private def hasDrivePrefix(path: String): Boolean = path.length >= 2 && path.charAt(1) == ':' &&
    ((path.charAt(0) >= 'A' && path.charAt(0) <= 'Z') || (path.charAt(0) >= 'a' && path.charAt(0) <= 'z'))

  private def normalize(path: String): String = {
    val slashPath = path.replace('\\', '/')
    val parts = slashPath.split("/", -1).filter(_.nonEmpty).toVector
    val components =
      if (hasDrivePrefix(slashPath)) {
        val rooted = slashPath.length >= 3 && slashPath.charAt(2) == '/'
        val prefix = slashPath.take(2) + (if (rooted) "/" else "")
        PathComponents(prefix, rooted, slashPath.substring(prefix.length).split("/", -1).filter(_.nonEmpty).toVector)
      } else if (slashPath.startsWith("//") && parts.size >= 2) {
        PathComponents(s"//${parts(0)}/${parts(1)}/", rooted = true, parts.drop(2))
      } else PathComponents(if (slashPath.startsWith("/")) "/" else "", slashPath.startsWith("/"), parts)
    val normalized = components.parts.foldLeft(Vector.empty[String]) {
      case (acc, "" | ".") => acc
      case (acc, "..") if acc.nonEmpty && acc.last != ".." => acc.dropRight(1)
      case (acc, "..") if components.rooted => acc
      case (acc, part) => acc :+ part
    }
    val result = components.prefix + normalized.mkString("/")
    if (result.isEmpty) "." else result
  }

  private final case class PathComponents(prefix: String, rooted: Boolean, parts: Vector[String])
}

final case class SourcePoint(offset: Int, line: Int, column: Int)

sealed trait SourceSpan
object SourceSpan {
  final case class Range(start: SourcePoint, end: SourcePoint) extends SourceSpan
  final case class Point(point: SourcePoint) extends SourceSpan
  case object Unavailable extends SourceSpan
}

sealed trait CompiledText
object CompiledText {
  final case class Available(text: String) extends CompiledText
  case object Unavailable extends CompiledText
}

final case class ExpressionSource(identity: SourceIdentity, span: SourceSpan, text: CompiledText)

sealed trait ObservationKind
object ObservationKind {
  case object BooleanLeaf extends ObservationKind
  case object BooleanOperator extends ObservationKind
  case object Comparison extends ObservationKind
  case object Operand extends ObservationKind
  case object Opaque extends ObservationKind
}

final case class ObservationSite(span: SourceSpan, text: CompiledText, kind: ObservationKind)

trait ValueRenderer {
  def render[A](value: A): String
}
object ValueRenderer {
  def standard: ValueRenderer = new ValueRenderer {
    override def render[A](value: A): String = String.valueOf(value)
  }
}

sealed trait CapturedValue {
  def render(renderer: ValueRenderer): String
}
object CapturedValue {
  def apply[A](value: A): CapturedValue = new CapturedValue {
    override def render(renderer: ValueRenderer): String = renderer.render(value)
  }
}

sealed trait Evaluation
object Evaluation {
  final case class Evaluated(value: CapturedValue) extends Evaluation
  case object NotEvaluated extends Evaluation
}

final case class Observation(site: ObservationSite, evaluation: Evaluation)
final case class AssertionDiagnostic(source: ExpressionSource, observations: Vector[Observation])

sealed trait ProvidedSource
object ProvidedSource {
  final case class Content(text: String) extends ProvidedSource
  case object Unavailable extends ProvidedSource
}

trait SourceProvider {
  def read(identity: SourceIdentity): ProvidedSource
}
object SourceProvider {
  def unavailable: SourceProvider = new SourceProvider {
    override def read(identity: SourceIdentity): ProvidedSource = ProvidedSource.Unavailable
  }
}

final case class RenderLimits(valueCharacters: Int, excerptCharacters: Int, observations: Int, totalCharacters: Int, tabWidth: Int) {
  require(valueCharacters > 0 && excerptCharacters > 0 && observations > 0 && totalCharacters > 0 && tabWidth > 0, "Nonpositive render limit")
}
object RenderLimits {
  def standard: RenderLimits = RenderLimits(256, 1024, 32, 4096, 4)
}

final case class AssertionContext(sourceRoot: SourceRoot, sourceProvider: SourceProvider, valueRenderer: ValueRenderer, limits: RenderLimits)
object AssertionContext {
  def standard: AssertionContext = AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, ValueRenderer.standard, RenderLimits.standard)
}

sealed trait SourceValidation
object SourceValidation {
  case object Matching extends SourceValidation
  case object Mismatch extends SourceValidation
  case object Unavailable extends SourceValidation
  case object RangeUnavailable extends SourceValidation
  final case class ProviderFailure(cause: Throwable) extends SourceValidation {
    lazy val message: RenderedErrorMessage = RenderedErrorMessage.capture(cause)
  }
}

final case class RenderingFailure(observationIndex: Int, cause: Throwable)

sealed trait RenderedErrorMessage
object RenderedErrorMessage {
  final case class Available(value: String) extends RenderedErrorMessage
  case object Unavailable extends RenderedErrorMessage
  final case class AccessorFailed(cause: Throwable) extends RenderedErrorMessage

  private[assertions] def capture(cause: Throwable): RenderedErrorMessage = {
    try Option(cause.getMessage).map(Available.apply).getOrElse(Unavailable)
    catch { case error: Throwable => AccessorFailed(error) }
  }
}

sealed trait RenderedValue
object RenderedValue {
  final case class Evaluated(text: String) extends RenderedValue
  case object NotEvaluated extends RenderedValue
  final case class RenderingFailed(cause: Throwable) extends RenderedValue {
    lazy val message: RenderedErrorMessage = RenderedErrorMessage.capture(cause)
  }
}

final case class RenderedAssertion(text: String, sourceValidation: SourceValidation, renderingFailures: Vector[RenderingFailure], values: Vector[RenderedValue])
