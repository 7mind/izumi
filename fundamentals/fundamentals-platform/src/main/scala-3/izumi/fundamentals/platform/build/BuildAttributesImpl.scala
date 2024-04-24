package izumi.fundamentals.platform.build

import java.time.LocalDateTime
import scala.quoted.{Expr, Quotes}

object BuildAttributesImpl {
  private lazy val compilationTime: LocalDateTime = LocalDateTime.now()

  def buildTimestampMacro()(using quotes: Quotes): Expr[LocalDateTime] = {
    '{
      LocalDateTime.of(
        ${ Expr(compilationTime.getYear) },
        ${ Expr(compilationTime.getMonthValue) },
        ${ Expr(compilationTime.getDayOfMonth) },
        ${ Expr(compilationTime.getHour) },
        ${ Expr(compilationTime.getMinute) },
        ${ Expr(compilationTime.getSecond) },
        ${ Expr(compilationTime.getNano) },
      )
    }
  }

  def sbtProjectRoot()(using quotes: Quotes): Expr[Option[String]] = {
    import quotes.reflect.*

    val result = SourceFile.current.getJPath
      .flatMap(findProjectRoot)
      .map(_.toFile.getCanonicalPath)

    Expr(result)
  }

  def getExprProp(name: Expr[String])(using quotes: Quotes): Expr[Option[String]] = {
    getProp(name.valueOrAbort)
  }

  def getProp(name: String)(using quotes: Quotes): Expr[Option[String]] = {
    Expr(Option(System.getProperty(name)))
  }

}
