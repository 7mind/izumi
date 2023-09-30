package izumi.fundamentals.platform.language

import scala.quoted.{Expr, Quotes, Type}

final case class SourceFilePositionMaterializer(get: SourceFilePosition) extends AnyVal

object SourceFilePositionMaterializer {
  inline def sourcePosition(implicit ev: SourceFilePositionMaterializer): SourceFilePosition = ev.get

  inline implicit def materialize: SourceFilePositionMaterializer = ${ SourceFilePositionMaterializerMacro.getSourceFilePositionMaterializer }

  object SourceFilePositionMaterializerMacro {
    def getSourceFilePositionMaterializer(using qctx: Quotes): Expr[SourceFilePositionMaterializer] = {
      val pos = getSourceFilePosition()
      '{ SourceFilePositionMaterializer(${ pos }) }
    }

    def getSourceFilePosition()(using qctx: Quotes): Expr[SourceFilePosition] = {
      val pos = qctx.reflect.Position.ofMacroExpansion
      val name = pos.sourceFile.name
      val line = pos.startLine + 1

      '{ SourceFilePosition(${ Expr(name) }, ${ Expr(line) }) }
    }
  }
}
