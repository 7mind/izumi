package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.language.SourceFilePositionMaterializer.SourcePositionMaterializerMacro

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  @inline def apply()(implicit ev: CodePositionMaterializer, dummy: DummyImplicit): CodePositionMaterializer = ev
  @inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get

  implicit def materialize: CodePositionMaterializer = macro CodePositionMaterializerMacro.getEnclosingPosition

  object CodePositionMaterializerMacro {

    def getEnclosingPosition(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
      import c.universe._
      val applicationPointId = getApplicationPointId(c)
      val sourceFilePosition = c.Expr[SourceFilePosition](SourcePositionMaterializerMacro.getSourceFilePosition(c))
      reify {
        CodePositionMaterializer(CodePosition(sourceFilePosition.splice, applicationPointId.splice))
      }
    }

    def getApplicationPointId(c: blackbox.Context): c.Expr[String] = {
      import c.universe._
      c.Expr[String](Literal(Constant(getApplicationPointIdImpl(c))))
    }

    def getApplicationPointIdImpl(c: blackbox.Context): String = {
      def goodSymbol(s: c.Symbol): Boolean = {
        val name = s.name.toString
        !name.startsWith("$") && !name.startsWith("<")
      }

      @tailrec
      def rec(s: c.Symbol, st: mutable.ArrayBuffer[c.Symbol]): Unit = {
        st.prepend(s)
        s.owner match {
          case c.universe.NoSymbol =>
          case o =>
            rec(o, st)

        }
      }

      val st = mutable.ArrayBuffer[c.Symbol]()
      rec(c.internal.enclosingOwner, st)

      st.tail
        .map {
          case s if s.isPackage => s.name
          case s if goodSymbol(s) => s.name
          case s =>
            if (s.isClass) {
              s.asClass.baseClasses.find(goodSymbol).map(_.name).getOrElse(s.pos.line)
            } else {
              s.pos.line
            }
        }
        .map(_.toString.trim)
        .mkString(".")
    }

  }

}
