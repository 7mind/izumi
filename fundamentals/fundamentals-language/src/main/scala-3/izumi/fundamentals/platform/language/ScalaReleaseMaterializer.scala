package izumi.fundamentals.platform.language

import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.quoted.{Expr, Quotes, Type}
import scala.util.matching.Regex

case class ScalaReleaseMaterializer(release: ScalaRelease)

object ScalaReleaseMaterializer {
  inline def scalaRelease(implicit ev: ScalaReleaseMaterializer): ScalaRelease = ev.release

  inline implicit def materialize: ScalaReleaseMaterializer = ${ ScalaReleaseMaterializer.doMaterialize }

  def doMaterialize(using qctx: Quotes): Expr[ScalaReleaseMaterializer] = {
    import qctx.reflect._

    ScalaRelease.parse(dotty.tools.dotc.config.Properties.versionNumberString) match {
      case ScalaRelease.`3`(minor, bugfix) =>
        '{ ScalaReleaseMaterializer( ScalaRelease.`3`(${ Expr(minor) }, ${ Expr(bugfix) }) ) }
      case other =>
        report.warning(s"Scala 3 expected, but something strange was extracted: $other ")
        other match {
          case ScalaRelease.Unsupported(parts) =>
            '{ ScalaReleaseMaterializer( ScalaRelease.Unsupported(${ Expr(parts) }) ) }
          case ScalaRelease.Unknown(string) =>
            '{ ScalaReleaseMaterializer( ScalaRelease.Unknown(${ Expr(string) }) ) }
          case _ =>
            report.errorAndAbort(s"Scala 3 expected, but Scala 2 was extracted: $other ")
        }
    }
  }

}
