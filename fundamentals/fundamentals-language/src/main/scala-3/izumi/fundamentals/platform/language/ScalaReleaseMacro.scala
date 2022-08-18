package izumi.fundamentals.platform.language

import scala.quoted.{Expr, Quotes, Type}

object ScalaReleaseMacro {

  def doMaterialize(using Quotes): Expr[ScalaRelease] = new ScalaReleaseMacro().getScalaRelease()

  private final class ScalaReleaseMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getScalaRelease(): Expr[ScalaRelease] = ???
  }
}

object IzScala {
  inline def scalaRelease: ScalaRelease = ${ ScalaReleaseMacro.doMaterialize }
}
