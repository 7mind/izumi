package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.language.IzScala.ScalaRelease

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object ScalaReleaseMacro {
    def scalaRelease: IzScala.ScalaRelease = macro scalaReleaseMacro

    def scalaReleaseMacro(c: blackbox.Context): c.Expr[IzScala.ScalaRelease] = {
      import c.universe.*

      ScalaRelease.parse(scala.util.Properties.versionNumberString) match {
        case ScalaRelease.`2_12`(bugfix) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.`2_12`].asClass.companion}($bugfix)")
        case ScalaRelease.`2_13`(bugfix) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.`2_13`].asClass.companion}($bugfix)")
        case ScalaRelease.`3_0`(bugfix) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.`3_0`].asClass.companion}($bugfix)")
        case ScalaRelease.Unsupported(parts) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.Unsupported].asClass.companion}(..${parts.toList})")
        case ScalaRelease.Unknown(verString) =>
          c.Expr[ScalaRelease](q"${symbolOf[ScalaRelease.Unknown].asClass.companion}($verString)")
      }
    }

  }
