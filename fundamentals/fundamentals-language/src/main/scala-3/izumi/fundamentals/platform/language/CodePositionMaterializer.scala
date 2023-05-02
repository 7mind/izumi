package izumi.fundamentals.platform.language

import scala.annotation.{tailrec, targetName}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  @targetName("applySummon")
  inline def apply()(implicit ev: CodePositionMaterializer): CodePositionMaterializer = ev
  inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  inline implicit def materialize: CodePositionMaterializer = ${ CodePositionMaterializerMacro.getCodePositionMaterializer() }

  inline def materializeApplicationPointId: String = ${ CodePositionMaterializerMacro.getApplicationPointId() }

  object CodePositionMaterializerMacro {

    def ownershipChain()(using qctx: Quotes): Seq[qctx.reflect.Symbol] = {
      import qctx.reflect.*

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

    def getApplicationPointId()(using qctx: Quotes): Expr[String] = {
      val st = ownershipChain()
      val applicationId = st.tail
        .flatMap {
          case s if s.isPackageDef =>
            Some(s.name)
          case s if s.isValDef =>
            None
          case s if goodSymbol(s) =>
            Some(s.name)
          case _ =>
            None
        }
        .map(_.trim)
        .mkString(".")

      Expr(applicationId)
    }

    def getEnclosingPosition()(using qctx: Quotes): Expr[CodePosition] = {
      val sourcePos = SourceFilePositionMaterializer.SourceFilePositionMaterializerMacro.getSourceFilePosition()
      val applicationId = getApplicationPointId()

      '{ CodePosition(${ sourcePos }, ${ applicationId }) }
    }

    def getCodePositionMaterializer()(using qctx: Quotes): Expr[CodePositionMaterializer] = {
      val pos = getEnclosingPosition()
      '{ CodePositionMaterializer(${ pos }) }
    }

    private def goodSymbol(using qctx: Quotes)(s: qctx.reflect.Symbol): Boolean = {
      val name = s.name
      !name.startsWith("$") && !name.startsWith("<")
    }
  }

}
