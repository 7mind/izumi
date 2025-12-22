package izumi.fundamentals.platform.language

import scala.quoted.{Expr, Quotes}

final case class SourceFilePositionMaterializer(get: SourceFilePosition) extends AnyVal

object SourceFilePositionMaterializer {
  inline def sourcePosition(implicit ev: SourceFilePositionMaterializer): SourceFilePosition = ev.get

  inline implicit def materialize: SourceFilePositionMaterializer = ${ SourceFilePositionMaterializerMacro.getSourceFilePositionMaterializer }

  object SourceFilePositionMaterializerMacro {
    def getSourceFilePositionMaterializer(using qctx: Quotes): Expr[SourceFilePositionMaterializer] = {
      val pos = getSourceFilePosition()
      '{ SourceFilePositionMaterializer(${ pos }): SourceFilePositionMaterializer }
    }

    def getSourceFilePosition()(using qctx: Quotes): Expr[SourceFilePosition] = {
      import qctx.reflect.*

      val pos = Position.ofMacroExpansion
      val name = pos.sourceFile.name
      val line = pos.startLine + 1

      // Use Typed nodes to avoid retypechecking (this required internal.setType on Scala 2, but seems like Typed(..) achieves the same on Scala 3)
      '{
        SourceFilePosition(
          ${ Literal(StringConstant(name)).asExpr.asInstanceOf[Expr[String]] }: String,
          ${ Literal(IntConstant(line)).asExpr.asInstanceOf[Expr[Int]] }: Int
        ): SourceFilePosition
      }
    }
  }
}
