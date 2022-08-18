package izumi.fundamentals.platform.language

import scala.annotation.tailrec
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  inline def apply()(implicit ev: CodePositionMaterializer, dummy: DummyImplicit): CodePositionMaterializer = ev
  inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  inline implicit def materialize: CodePositionMaterializer = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[CodePositionMaterializer] = new CodePositionMaterializerMacro().getEnclosingPosition()


  private final class CodePositionMaterializerMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getEnclosingPosition(): Expr[CodePositionMaterializer] = ???
  }

}
