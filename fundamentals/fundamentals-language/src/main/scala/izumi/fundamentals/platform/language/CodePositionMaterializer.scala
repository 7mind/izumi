package izumi.fundamentals.platform.language

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class CodePositionMaterializer(get: CodePosition) extends AnyVal

object CodePositionMaterializer {
  @inline def apply()(implicit ev: CodePositionMaterializer, dummy: DummyImplicit): CodePositionMaterializer = ev
  @inline def codePosition(implicit ev: CodePositionMaterializer): CodePosition = ev.get
  @inline def sourcePosition(implicit ev: CodePositionMaterializer): SourceFilePosition = ev.get.position

  implicit def izCodePos: CodePositionMaterializer = macro CodePositionMaterializerMacro.getEnclosingPosition

  object CodePositionMaterializerMacro {
    def getEnclosingPosition(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
      import c.universe._
      val cp = getCodePosition(c)
      reify {
        CodePositionMaterializer(cp.splice)
      }
    }

    def getCodePosition(c: blackbox.Context): c.Expr[CodePosition] = {
      import c.universe._
      getEnclosingPositionImpl(c) match {
        case CodePosition(SourceFilePosition(file, line), applicationPointId) =>
          reify {
            CodePosition.apply(
              SourceFilePosition(
                c.Expr[String](Literal(Constant(file))).splice,
                c.Expr[Int](Literal(Constant(line))).splice
              ),
              applicationPointId = c.Expr[String](Literal(Constant(applicationPointId))).splice
            )
          }
      }
    }

    def getApplicationPointId(c: blackbox.Context): c.Expr[String] = {
      import c.universe._
      c.Expr[String](Literal(Constant(getEnclosingPositionImpl(c).applicationPointId)))
    }

    private def getEnclosingPositionImpl(c: blackbox.Context): CodePosition = {

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

      val normalizedName = st
        .tail
        .map {
          case s if s.isPackage => s.name
          case s if goodSymbol(s) => s.name
          case s => if (s.isClass) {
            s.asClass.baseClasses.find(goodSymbol).map(_.name).getOrElse(s.pos.line)
          } else {
            s.pos.line
          }
        }
        .map(_.toString.trim)
        .mkString(".")

      CodePosition(
        SourceFilePosition(
          line = c.enclosingPosition.line,
          file = c.enclosingPosition.source.file.name
        ),
        applicationPointId = normalizedName
      )
    }
  }
}
