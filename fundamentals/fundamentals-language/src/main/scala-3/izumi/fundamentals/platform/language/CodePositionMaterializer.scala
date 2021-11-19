package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.language.SourceFilePositionMaterializer.SourcePositionMaterializerMacro

import scala.annotation.tailrec
import scala.collection.mutable

final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  @inline def apply()(implicit ev: CodePositionMaterializer, dummy: DummyImplicit): CodePositionMaterializer = ev
  @inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  inline implicit def materialize: CodePositionMaterializer = ${ CodePositionMaterializerMacro.getEnclosingPosition }

  object CodePositionMaterializerMacro {

    def getEnclosingPosition(using quoted.Quotes): quoted.Expr[CodePositionMaterializer] = {
      '{
        CodePositionMaterializer.apply(
          CodePosition.apply(
            ${ SourcePositionMaterializerMacro.getSourceFilePosition },
            ${ getApplicationPointId },
          )
        )
      }
    }

    def getApplicationPointId(using quoted.Quotes): quoted.Expr[String] = {
      quoted.Expr(getApplicationPointIdImpl)
    }

    def getApplicationPointIdImpl(using quotes: quoted.Quotes): String = {
      def goodSymbol(s: quotes.reflect.Symbol): Boolean = {
        val name = s.name.toString
        !name.startsWith("$") && !name.startsWith("<")
      }

      @tailrec
      def rec(s: quotes.reflect.Symbol, st: mutable.ArrayBuffer[quotes.reflect.Symbol]): Unit = {
        st.prepend(s)
        s.owner match {
          case s if s.isNoSymbol =>
          case o =>
            rec(o, st)

        }
      }

      val st = mutable.ArrayBuffer[quotes.reflect.Symbol]()
      rec(quotes.reflect.Symbol.spliceOwner, st)

      st.tail
        .map {
          case s if s.isPackageDef => s.name
          case s if goodSymbol(s) => s.name
          case s =>
            (s.tree match {
              case tt: quotes.reflect.TypeTree =>
                Some(tt.tpe)
              case _ =>
                None
            }).flatMap(_.baseClasses.find(goodSymbol).map(_.name))
              .orElse(s.pos.map(_.startLine + 1))
              .getOrElse(s.tree.pos)
        }
        .map(_.toString.trim)
        .mkString(".")
    }

  }

}
