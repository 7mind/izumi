package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.language.IzScala.ScalaRelease
import scala.quoted.{Expr, Quotes, Type}

object ScalaReleaseMacro {
  inline def scalaRelease: IzScala.ScalaRelease = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[IzScala.ScalaRelease] = new ScalaReleaseMacro().getScalaRelease()

  private final class ScalaReleaseMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getScalaRelease(): Expr[IzScala.ScalaRelease] = ???
  }
}
