package izumi.fundamentals.platform.language

final case class SourceFilePositionMaterializer(get: SourceFilePosition) extends AnyVal

object SourceFilePositionMaterializer {
  @inline def sourcePosition(implicit ev: SourceFilePositionMaterializer): SourceFilePosition = ev.get

  inline implicit def materialize: SourceFilePositionMaterializer = ${ SourcePositionMaterializerMacro.getSourceFilePositionMaterializer }

  object SourcePositionMaterializerMacro {

    // NOTE: these Scala 3 macros do not do any manual type setting as Scala 2's do,
    //       YET. We should check whether these are necessary anymore, most likely
    //       they still are in which case we should return these optimizations

    def getSourceFilePositionMaterializer(using quoted.Quotes): quoted.Expr[SourceFilePositionMaterializer] = {
      '{ SourceFilePositionMaterializer.apply(${ getSourceFilePosition }) }
    }

    def getSourceFilePosition(using quoted.Quotes): quoted.Expr[SourceFilePosition] = {
      val pos = quoted.quotes.reflect.Position.ofMacroExpansion
      '{ SourceFilePosition.apply(${ quoted.Expr(pos.sourceFile.name) }, ${ quoted.Expr(pos.startLine + 1) }) }
    }

    def getSourceFilePositionValue(using quoted.Quotes): SourceFilePosition = {
      val pos = quoted.quotes.reflect.Position.ofMacroExpansion
      SourceFilePosition(pos.sourceFile.name, pos.startLine + 1)
    }

  }

}
