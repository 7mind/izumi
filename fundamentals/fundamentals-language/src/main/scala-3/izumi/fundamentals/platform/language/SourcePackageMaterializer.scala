package izumi.fundamentals.platform.language

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

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
      '{ SourcePackageMaterializer(SourcePackage(${ packageStr })) }
    }

    def getSourcePackageString()(using qctx: Quotes): Expr[String] = {
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

      Expr(applicationIdPkgOnly)
    }
  }
}
