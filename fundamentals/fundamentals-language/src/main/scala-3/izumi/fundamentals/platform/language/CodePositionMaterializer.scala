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

  inline def packageOf[A]: String = ${ CodePositionMaterializerMacro.getPackageOf[A]() }

  class Extractors[Q <: Quotes](using val qctx: Q) extends AnyVal {
    def ownershipChainOf(sym: qctx.reflect.Symbol): Seq[qctx.reflect.Symbol] = {
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
      extractOwnershipChain(sym, st)
      st.toSeq
    }

    def getApplicationPointIdOf(chain: Seq[qctx.reflect.Symbol]): Expr[String] = {
      val applicationId = chain.tail
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

    private def goodSymbol(using qctx: Quotes)(s: qctx.reflect.Symbol): Boolean = {
      val name = s.name
      !name.startsWith("$") && !name.startsWith("<")
    }
  }

  object CodePositionMaterializerMacro {
    def getPackageOf[A: Type]()(using qctx: Quotes): Expr[String] = {
      import qctx.reflect.*
      val ext = new Extractors[qctx.type]
      ext.getApplicationPointIdOf(ext.ownershipChainOf(TypeRepr.of[A].typeSymbol))
    }

    def ownershipChain()(using qctx: Quotes): Seq[qctx.reflect.Symbol] = {
      val ext = new Extractors[qctx.type]
      ext.ownershipChainOf(qctx.reflect.Symbol.spliceOwner)
    }

    def getApplicationPointId()(using qctx: Quotes): Expr[String] = {
      val ext = new Extractors[qctx.type]
      ext.getApplicationPointIdOf(ownershipChain())
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

  }

}
