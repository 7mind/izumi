package izumi.fundamentals.platform.language

import scala.annotation.tailrec
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

final case class SourcePackageMaterializer(get: SourcePackage) extends AnyVal

object SourcePackageMaterializer {
  inline def apply()(implicit ev: SourcePackageMaterializer, dummy: DummyImplicit): SourcePackageMaterializer = ev
  inline def thisPkg(implicit pkg: SourcePackageMaterializer): String = pkg.get.pkg

  inline implicit def materialize: SourcePackageMaterializer = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[SourcePackageMaterializer] = new SourcePackageMaterializerMacro().getSourcePackage()


  private final class SourcePackageMaterializerMacro(using qctx: Quotes) {
    import qctx.reflect._

    def getSourcePackage(): Expr[SourcePackageMaterializer] = {
      val applicationId = getApplicationPointId()
      '{ SourcePackageMaterializer( SourcePackage( ${applicationId}) ) }
    }

    def getApplicationPointId(): Expr[String] = {
      val st = ownershipChain()

      val applicationId = st.tail.flatMap {
        case s if s.isPackageDef =>
          Some(s.name)
        case _ =>
          None
      }
        .map(_.toString.trim)
        .mkString(".")

      Expr(applicationId)
    }

    private def ownershipChain(): Seq[Symbol] = { // TODO: copy-paste from CodePositionMaterializer, deduplicate
      @tailrec
      def extractOwnershipChain(s: Symbol, st: mutable.ArrayBuffer[Symbol]): Unit = {
        st.prepend(s)
        s.maybeOwner match {
          case n if n.isNoSymbol =>
          case o =>
            extractOwnershipChain(o, st)

        }
      }
      val st = mutable.ArrayBuffer[Symbol]()
      extractOwnershipChain(Symbol.spliceOwner, st)
      st.toSeq
    }
  }
}
