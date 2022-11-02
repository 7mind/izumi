package izumi.fundamentals.platform.language

import scala.quoted.{Expr, Quotes, Type}

final case class SourceFilePositionMaterializer(get: SourceFilePosition) extends AnyVal

object SourceFilePositionMaterializer {
  inline def sourcePosition(implicit ev: SourceFilePositionMaterializer): SourceFilePosition = ev.get
  inline implicit def materialize: SourceFilePositionMaterializer = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[SourceFilePositionMaterializer] = new SourceFilePositionMaterializerMacro().getSourceFilePositionMat()

  private[language] final class SourceFilePositionMaterializerMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getSourceFilePositionMat(): Expr[SourceFilePositionMaterializer] = {
      val pos = getSourceFilePosition()
      '{ SourceFilePositionMaterializer(${ pos }) }
    }

    def getSourceFilePosition(): Expr[SourceFilePosition] = {
      val pos = Position.ofMacroExpansion
      val name = pos.sourceFile.name
      val line = pos.startLine + 1

      '{ SourceFilePosition(${ Expr(name) }, ${ Expr(line) }) }
    }
  }
}
