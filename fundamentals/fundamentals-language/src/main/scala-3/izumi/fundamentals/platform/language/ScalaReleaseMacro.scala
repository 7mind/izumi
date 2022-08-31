package izumi.fundamentals.platform.language


import izumi.fundamentals.platform.jvm.IzClasspath

import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.quoted.{Expr, Quotes, Type}
import scala.util.matching.Regex

object ScalaReleaseMacro {

  def doMaterialize(using Quotes): Expr[ScalaRelease] = new ScalaReleaseMacro().getScalaRelease

  private final class ScalaReleaseMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getScalaRelease: Expr[ScalaRelease] = {
      val cp = IzClasspath.safeClasspathSeq()
      val re = "scala3-compiler_3/([^/]+)/".r.unanchored
      val version = cp.collect {
        case re(a) => a
      }
      version.lastOption match {
        case Some(value) =>
          ScalaRelease.parse(value) match {
            case ScalaRelease.`3`(minor, bugfix) =>
              '{ ScalaRelease.`3`( ${ Expr(minor)}, ${ Expr(bugfix)} )}
            case o =>
              report.errorAndAbort(s"Scala 3 expected, but something strange was extracted: $o ")
          }
        case None =>
          report.errorAndAbort(s"Can't extract Scala 3 version from classpath $cp")
      }
    }
  }
}

object IzScala {
  inline def scalaRelease: ScalaRelease = ${ ScalaReleaseMacro.doMaterialize }
}

