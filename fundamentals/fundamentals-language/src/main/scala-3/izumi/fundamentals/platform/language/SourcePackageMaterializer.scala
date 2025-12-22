package izumi.fundamentals.platform.language

import scala.annotation.targetName
import scala.quoted.{Expr, Quotes}

final case class SourcePackageMaterializer(get: SourcePackage) extends AnyVal

object SourcePackageMaterializer {
  @targetName("applySummon")
  inline def apply()(implicit ev: SourcePackageMaterializer): SourcePackageMaterializer = ev
  inline def thisPkg(implicit pkg: SourcePackageMaterializer): String = pkg.get.pkg

  inline implicit def materialize: SourcePackageMaterializer = ${ SourcePackageMaterializerMacro.getSourcePackageMaterializer() }

  inline def materializeSourcePackageString: String = ${ SourcePackageMaterializerMacro.getSourcePackageString() }

  object SourcePackageMaterializerMacro {
    def getSourcePackageMaterializer()(using qctx: Quotes): Expr[SourcePackageMaterializer] = {
      val packageStr = getSourcePackageString()
      '{ SourcePackageMaterializer(SourcePackage(${ packageStr }): SourcePackage): SourcePackageMaterializer }
    }

    def getSourcePackageString()(using qctx: Quotes): Expr[String] = {
      import qctx.reflect.*

      val st = CodePositionMaterializer.CodePositionMaterializerMacro.ownershipChain()

      val applicationIdPkgOnly = st.tail
        .flatMap {
          case s if s.isPackageDef =>
            Some(s.name)
          case _ =>
            None
        }
        .map(_.toString.trim)
        .mkString(".")

      Typed(Literal(StringConstant(applicationIdPkgOnly)), TypeTree.of[String]).asExpr.asInstanceOf[Expr[String]]
    }
  }
}
