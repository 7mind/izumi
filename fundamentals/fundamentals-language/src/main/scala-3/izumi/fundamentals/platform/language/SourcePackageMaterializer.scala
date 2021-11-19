package izumi.fundamentals.platform.language

final case class SourcePackageMaterializer(get: SourcePackage) extends AnyVal

object SourcePackageMaterializer {
  @inline def apply()(implicit ev: SourcePackageMaterializer, dummy: DummyImplicit): SourcePackageMaterializer = ev
  @inline def thisPkg(implicit pkg: SourcePackageMaterializer): String = pkg.get.pkg

  inline def materialize: SourcePackageMaterializer = ${ SourcePackageMaterializerMacro.getSourcePackage }

  object SourcePackageMaterializerMacro {
    // copypaste from lihaoyi 'sourcecode' https://github.com/lihaoyi/sourcecode/blob/d32637de59d5ecdd5040d1ef06a9370bc46a310f/sourcecode/shared/src/main/scala/sourcecode/SourceContext.scala
    def getSourcePackage(using quoted.Quotes): quoted.Expr[SourcePackageMaterializer] = {
      '{ SourcePackageMaterializer(SourcePackage(${ quoted.Expr(getSourcePackageString) })) }
    }

    def getSourcePackageString(using quoted.Quotes): String = {
      var current = quoted.quotes.reflect.Symbol.spliceOwner
      var path = List.empty[String]
      while (current.isNoSymbol && current != quoted.quotes.reflect.defn.RootPackage) {
        if (current.isPackageDef) {
          path = current.name :: path
        }
        current = current.owner
      }
      val renderedPath = path.mkString(".")
      renderedPath
    }
  }
}
