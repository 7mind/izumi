package izumi.distage.constructors

import izumi.distage.constructors.{AnyConstructor, AnyConstructorOptionalMakeDSL, ClassConstructorMacro, TraitConstructor, TraitConstructorMacro}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.quoted.{Expr, Quotes, Type}

object AnyConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[AnyConstructor[R]] = try {
    import qctx.reflect.{*, given}

    val tpe0 = TypeRepr.of[R].dealias.simplified
    val typeSymbol = tpe0.typeSymbol

    // FIXME remove redundant check across macros
    val util = new ConstructorUtil[qctx.type]()
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "AnyConstructor")

    // FIXME remove redundant check across macros
    lazy val context = new ConstructorContext[R, qctx.type, util.type](util)

    if (AndTypeTypeTest.unapply(tpe0).isDefined || RefinementTypeTest.unapply(tpe0).isDefined) {
      // ignore intersections for now
      '{ (throw new RuntimeException("unsupported intersection")): AnyConstructor[R] }
    } else if ((tpe0.classSymbol.isDefined && !typeSymbol.flags.is(Flags.Trait) && !typeSymbol.flags.is(Flags.Abstract)) || {
        util.dereferenceTypeRef(tpe0) match { case _: ConstantType | _: TermRef => true; case _ => false }
      }) {
      ClassConstructorMacro.makeImpl[R](util)
    } else if ({
      // TODO: check for sealed
      context.isWireableTrait
    }) {
      TraitConstructorMacro.makeImpl[R](util, context)
    } else if (context.isFactory) {
      report.errorAndAbort(
        s"""AnyConstructor failure: ${Type.show[R]} is a Factory, use makeFactory or fromFactory to wire factories.""".stripMargin
      )
    } else {
      report.errorAndAbort(
        s"""AnyConstructor failure: couldn't generate a constructor for ${Type.show[R]}!
           |It's neither a concrete class, nor a wireable trait or abstract class!""".stripMargin
      )
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  @experimental
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

    Expr.summon[AnyConstructorOptionalMakeDSL[T]] match {
      case Some(ctor) =>
        applyMake[T, BT](outerClass)('{ $ctor.provider })
      case None =>
        makeMethodImpl[T, BT](outerClass)
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  @experimental
  private def applyMake[T: Type, BT: Type](using qctx: Quotes)(outerClass: qctx.reflect.Symbol)(functoid: Expr[Functoid[T]]): Expr[BT] = {
    import qctx.reflect.*

    val tagT = '{ compiletime.summonInline[Tag[T]] }
    val codep = '{ compiletime.summonInline[CodePositionMaterializer] }

    Apply(Apply(TypeApply(Select.unique(This(outerClass), "_make"), List(TypeTree.of[T])), List(functoid.asTerm)), List(tagT.asTerm, codep.asTerm)).asExprOf[BT]
  }

  @experimental
  private def makeMethodImpl[T: Type, BT: Type](using qctx: Quotes)(outerClass: qctx.reflect.Symbol): Expr[BT] = {
    import qctx.reflect.*

    def findPos(p: Position, t: Tree): Tree = {
      new TreeAccumulator[Option[Tree]] {
        override def foldTree(x: Option[Tree], tree: Tree)(owner: Symbol): Option[Tree] = {
          if (x.isDefined) {
            x
          } else {
            val treeStart =
              try {
                tree.pos.start
              } catch { case _: Throwable => 0 }

            if (treeStart != 0 && treeStart == p.start) {
              Some(tree)
            } else {
              foldOverTree(x, tree)(owner)
            }
          }
        }
      }.foldOverTree(None, t)(Symbol.noSymbol)
        .getOrElse {
          report.errorAndAbort(s"Couldn't find position=$p in tree=${t.show}, all positions=${allPos(t)}")
        }
    }

    def allPos(t: Tree): List[Position] = {
      new TreeAccumulator[List[Position]] {
        override def foldTree(x: List[Position], tree: Tree)(owner: Symbol): List[Position] = {
          foldOverTree(x, tree)(owner) :+ tree.pos
        }
      }.foldOverTree(Nil, t)(Symbol.noSymbol)
    }

    var stopPos = List.empty[(Position, String)]

    extension (biggerPos: Position) {
      def contains(t: Tree, smallerPos: Position): Boolean = {
        val res = biggerPos.start <= smallerPos.start
          && biggerPos.end >= smallerPos.end
        if !res then stopPos = stopPos :+ (biggerPos, t.show)
        res
      }
    }

    def allMethodsCalledOnPosition(macroPos: Position, t0: Tree): List[String] = {
      new TreeAccumulator[List[String]] {
        override def foldTree(x: List[String], tree: Tree)(owner: Symbol): List[String] = {
          def continueIfContainsPos(t: Tree): List[String] = {
            if (t.pos.contains(t, macroPos)) {
              foldOverTree(x, tree)(owner)
            } else {
              x
            }
          }
          tree match {
            case Select(t, name) =>
              name :: continueIfContainsPos(t)
            case t =>
              continueIfContainsPos(t)
          }
        }
      }.foldOverTree(Nil, t0)(Symbol.noSymbol)
    }

    val outerowner = {
      val outerExpr = Symbol.spliceOwner.owner
      if (outerExpr.isLocalDummy) outerExpr.owner else outerExpr
    }

    val foundPos = findPos(Position.ofMacroExpansion, outerowner.tree)

    val foundMethods = allMethodsCalledOnPosition(Position.ofMacroExpansion, foundPos)

    val fromLikeMethods = foundMethods.filter(!ModuleDefDSL.MakeDSLNoOpMethodsWhitelist.contains(_))

    // FIXME remove redundant wrapping and .provider call
    val functoid: Expr[Functoid[T]] =
      if (fromLikeMethods.isEmpty) {
        '{ ${ AnyConstructorMacro.make[T] }.provider }
      } else {
        '{ AnyConstructorOptionalMakeDSL.errorConstructor[T](${ Expr(Type.show[T]) }, ${ Expr(fromLikeMethods) }).provider }
      }

    val res = applyMake[T, BT](outerClass)(functoid)

    import Printer.TreeStructure
//    report.warning(
//      s"""Splice owner tree: ${Symbol.spliceOwner.tree.show}:${Symbol.spliceOwner.pos} (macro:${Position.ofMacroExpansion})
//         |Splice owner-owner tree: ${Symbol.spliceOwner.owner.tree}:${Symbol.spliceOwner.owner.pos}
//         |Splice outer tree: ${outerowner.tree.show}:${outerowner.pos}
//         |allPos: ${allPos(outerowner.tree)}
//         |stopPos: $stopPos
//         |findPos: ${foundPos.show}
//         |findPosTree: $foundPos
//         |allCalledMethods: $foundMethods
//         |fromLikeMethods: $fromLikeMethods
//         |res: ${res.show}
//         |resTree: ${res.asTerm}
//         |""".stripMargin
//    )

    res
  }

}
