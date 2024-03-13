package izumi.fundamentals.platform.exceptions

import izumi.fundamentals.platform.exceptions.Issue.IssueContext
import izumi.fundamentals.platform.language.{SourceFilePosition, SourceFilePositionMaterializer}

trait Issue { this: Product =>

  val context: IssueContext

  override def toString: String = {
    this.productIterator
      .map(_.toString)
      .mkString(s"$productPrefix(", ",", ")") + s" $context"
  }
}

object Issue {
  final case class IssueContext(sourceFilePosition: SourceFilePosition, stackTrace: Throwable) {

    import izumi.fundamentals.platform.exceptions.IzThrowable.*
    import izumi.fundamentals.platform.strings.IzString.*

    override def toString: String =
      s"""$sourceFilePosition {
         |${stackTrace.stacktraceString.shift(4)}
         |}""".stripMargin
  }

  object IssueContext {
    implicit def materializeExceptionContext(
      implicit pos: SourceFilePositionMaterializer
    ): IssueContext = new IssueContext(pos.get, new Throwable())
  }
}
