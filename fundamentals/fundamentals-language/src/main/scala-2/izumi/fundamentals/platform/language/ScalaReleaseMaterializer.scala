package izumi.fundamentals.platform.language

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

case class ScalaReleaseMaterializer(release: ScalaRelease)

object ScalaReleaseMaterializer {
  @inline def scalaRelease(implicit ev: ScalaReleaseMaterializer): ScalaRelease = ev.release

  implicit def materialize: ScalaReleaseMaterializer = macro scalaReleaseMacro

  def scalaReleaseMacro(c: blackbox.Context): c.Expr[ScalaReleaseMaterializer] = {
    import c.universe.*

    ScalaRelease.parse(scala.util.Properties.versionNumberString) match {
      case ScalaRelease.`2_12`(bugfix) =>
        reify {
          ScalaReleaseMaterializer(ScalaRelease.`2_12`(c.Expr[Int](q"$bugfix").splice))
        }
      case ScalaRelease.`2_13`(bugfix) =>
        reify {
          ScalaReleaseMaterializer(ScalaRelease.`2_13`(c.Expr[Int](q"$bugfix").splice))
        }

      case other =>
        c.warning(c.enclosingPosition, s"Scala 2 expected but something strange was extracted: $other")
        other match {
          case ScalaRelease.Unsupported(parts) =>
            reify {
              ScalaReleaseMaterializer(ScalaRelease.Unsupported(c.Expr[List[Int]](q"..${parts.toList}").splice))
            }
          case ScalaRelease.Unknown(verString) =>
            reify {
              ScalaReleaseMaterializer(ScalaRelease.Unknown(c.Expr[String](q"$verString").splice))
            }
          case _ =>
            c.abort(c.enclosingPosition, s"Scala 2 expected, but Scala 3 was extracted: $other")
        }
    }
  }

}
