package izumi.fundamentals.platform.language

import scala.annotation.tailrec
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}


final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  inline def apply()(implicit ev: CodePositionMaterializer, dummy: DummyImplicit): CodePositionMaterializer = ev
  inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get
  inline def applicationPointId: String = ${ doapplicationPointId }

  inline implicit def materialize: CodePositionMaterializer = ${ doMaterialize }

  private def doMaterialize(using Quotes): Expr[CodePositionMaterializer] = new CodePositionMaterializerMacro().getEnclosingPositionMat()
  private def doapplicationPointId(using Quotes): Expr[String] = new CodePositionMaterializerMacro().getApplicationPointId()


  private final class CodePositionMaterializerMacro(using val qctx: Quotes) {
    import qctx.reflect._

    private def ownershipChain(): Seq[Symbol] = {
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

    def getApplicationPointId(): Expr[String] = {
      val st = ownershipChain()
      val applicationId = st.tail.flatMap {
        case s if s.isPackageDef =>
          Some(s.name)
        case s if s.isValDef =>
          None
        case s if goodSymbol(s) =>
          Some(s.name)
        case s =>
          None
      }
        .map(_.toString.trim)
        .mkString(".")

      Expr(applicationId)
    }

    def getEnclosingPosition(): Expr[CodePosition] = {
      val sourcePos = new SourceFilePositionMaterializer.SourceFilePositionMaterializerMacro().getSourceFilePosition()
      val applicationId = getApplicationPointId()

      '{ CodePosition( ${sourcePos}, ${applicationId}) }
    }

    def getEnclosingPositionMat(): Expr[CodePositionMaterializer] = {
      val pos = getEnclosingPosition()
      '{ CodePositionMaterializer( ${ pos } )}
    }

    private def goodSymbol(s: Symbol): Boolean = {
      val name = s.name.toString
      !name.startsWith("$") && !name.startsWith("<")
    }
  }

}
