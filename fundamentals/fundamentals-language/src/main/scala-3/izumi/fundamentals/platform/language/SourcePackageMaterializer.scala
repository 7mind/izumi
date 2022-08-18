package izumi.fundamentals.platform.language

import scala.quoted.{Expr, Quotes, Type}

final case class SourcePackageMaterializer(get: SourcePackage) extends AnyVal

object SourcePackageMaterializer {
  inline def apply()(implicit ev: SourcePackageMaterializer, dummy: DummyImplicit): SourcePackageMaterializer = ev
  inline def thisPkg(implicit pkg: SourcePackageMaterializer): String = pkg.get.pkg

  inline implicit def materialize: SourcePackageMaterializer = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[SourcePackageMaterializer] = new SourcePackageMaterializerMacro().getSourcePackage()


  private final class SourcePackageMaterializerMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getSourcePackage(): Expr[SourcePackageMaterializer] = ???
  }
}
