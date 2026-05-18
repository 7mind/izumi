package izumi.distage.constructors

import izumi.distage.constructors.{ClassConstructorMacro, ClassConstructorOptionalMakeDSL}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.quoted.{Expr, Quotes, Type}

object MakeMacro {

  def makeMethod[T: Type, BT: Type](using qctx: Quotes): Expr[BT] = try {
    import qctx.reflect.*

    def goGetOuterClass(s: Symbol): Symbol = {
      if (s.isClassDef) {
        s
      } else {
        goGetOuterClass(s.owner)
      }
    }
    val outerClass = goGetOuterClass(Symbol.spliceOwner)

    Expr.summon[ClassConstructorOptionalMakeDSL[T]] match {
      case Some(ctor) =>
        applyMake[T, BT](outerClass)('{ ${ ctor }.provider })
      case None =>
        makeMethodImpl[T, BT](outerClass)
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stacktraceString) }

  private def applyMake[T: Type, BT: Type](using qctx: Quotes)(outerClass: qctx.reflect.Symbol)(functoid: Expr[Functoid[T]]): Expr[BT] = {
    import qctx.reflect.*

    val tagT = '{ compiletime.summonInline[Tag[T]] }
    val codep = '{ compiletime.summonInline[CodePositionMaterializer] }

    Apply(Apply(TypeApply(Select.unique(This(outerClass), "_make"), List(TypeTree.of[T])), List(functoid.asTerm)), List(tagT.asTerm, codep.asTerm)).asExpr
      .asInstanceOf[Expr[BT]]
  }

  private def makeMethodImpl[T: Type, BT: Type](using qctx: Quotes)(outerClass: qctx.reflect.Symbol): Expr[BT] = {
    import qctx.reflect.*

    def findPos(macroPos: Position, macroSourceFile: SourceFile, t: Tree): Tree = {
      new TreeAccumulator[Option[Tree]] {
        override def foldTree(accum: Option[Tree], tree: Tree)(owner: Symbol): Option[Tree] = {
          if (accum.isDefined) {
            accum
          } else {
            val treeFile =
              try {
                tree.pos.sourceFile
              } catch { case _: Throwable => null }
            val treeStart =
              try {
                tree.pos.start
              } catch { case _: Throwable => 0 }

            if (treeStart != 0 && treeStart == macroPos.start && macroSourceFile == treeFile) {
              Some(tree)
            } else {
              foldOverTree(accum, tree)(owner)
            }
          }
        }
      }.foldOverTree(None, t)(Symbol.noSymbol)
        .getOrElse {
          report.errorAndAbort(
            "You MUST enable -Yretain-trees compiler option for distage to work!\n" +
            s"Couldn't find Position=$macroPos\n in Tree=${t.show}\n All positions=${allPos(t)}"
          )
        }
    }

    def allPos(t: Tree): List[Position] = {
      new TreeAccumulator[List[Position]] {
        override def foldTree(accum: List[Position], tree: Tree)(owner: Symbol): List[Position] = {
          foldOverTree(accum, tree)(owner) :+ tree.pos
        }
      }.foldOverTree(Nil, t)(Symbol.noSymbol)
    }

//    var stopPos = List.empty[(Position, String)]

    extension (biggerPos: Position) {
      // `.contains` method exists in Position itself, but isn't exported: https://github.com/scala/scala3/discussions/25916
      def _contains(smallerPos: Position): Boolean = {
        biggerPos.start <= smallerPos.start
        && biggerPos.end >= smallerPos.end
        && biggerPos.sourceFile == smallerPos.sourceFile
      }
    }

    def allMethodsCalledOnPosition(macroPos: Position, t0: Tree): List[String] = {
      new TreeAccumulator[List[String]] {
        override def foldTree(oldAccum: List[String], tree: Tree)(owner: Symbol): List[String] = {
          val (t, newAccum) = tree match {
            case Select(t, name) =>
              (t, name :: oldAccum)
            case t =>
              (t, oldAccum)
          }
          if (t.pos._contains(macroPos)) {
            foldOverTree(newAccum, tree)(owner)
          } else {
//            stopPos = stopPos :+ (t.pos, t.show)
            oldAccum // ignore the last method - presumably the `make`/`makeRole` call itself.
          }
        }
      }.foldOverTree(Nil, t0)(Symbol.noSymbol)
    }

    val outerowner = {
      val outerExpr = Symbol.spliceOwner.owner
      if (outerExpr.isLocalDummy) outerExpr.owner else outerExpr
    }

    val foundMethods = {
      val macroPos = Position.ofMacroExpansion
      val foundPos = findPos(macroPos, macroPos.sourceFile, outerowner.tree)
      allMethodsCalledOnPosition(macroPos, foundPos)
    }

    val fromLikeMethods = foundMethods.filter(!ModuleDefDSL.MakeDSLNoOpMethodsWhitelist.contains(_))

    // FIXME remove redundant wrapping and .provider call
    val functoid: Expr[Functoid[T]] =
      if (fromLikeMethods.isEmpty) {
        '{ ${ ClassConstructorMacro.make[T] }.provider }
      } else {
        '{ ClassConstructorOptionalMakeDSL.errorConstructor[T](${ Expr(Type.show[T]) }, ${ Expr(fromLikeMethods) }).provider }
      }

    val res = applyMake[T, BT](outerClass)(functoid)

//    {
//      given Printer[Tree] = Printer.TreeCode
//      report.warning(
//        s"""Splice owner tree: ${Symbol.spliceOwner.tree.show}:${Symbol.spliceOwner.pos} (macro:${Position.ofMacroExpansion})
//           |Splice owner-owner tree: ${Symbol.spliceOwner.owner.tree}:${Symbol.spliceOwner.owner.pos}
//           |Splice outer tree: ${outerowner.tree.show}:${outerowner.pos}
//           |allPos: ${allPos(outerowner.tree)}
//           |stopPos: $stopPos
//           |findPos: ${foundPos.show}
//           |findPosTree: $foundPos
//           |allCalledMethods: $foundMethods
//           |fromLikeMethods: $fromLikeMethods
//           |res: ${res.show}
//           |resTree: ${res.asTerm}
//           |""".stripMargin
//      )
//    }

    res
  }

}
