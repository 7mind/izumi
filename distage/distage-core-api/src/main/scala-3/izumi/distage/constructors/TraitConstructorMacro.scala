package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.reflect.WeakTag

import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object TraitConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[TraitConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    import util.ParamRepr
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "TraitConstructor")

    val context = new ConstructorContext[R, qctx.type, util.type](util)

    makeImpl[R](util, context)
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stacktraceString) }

  def makeImpl[R: Type](using qctx: Quotes)(util: ConstructorUtil[qctx.type], context: ConstructorContext[R, qctx.type, util.type]): Expr[TraitConstructor[R]] = {
    import qctx.reflect.*
    import util.{MemberRepr, ParamRepr}
    import context.{flatCtorParams, methodDecls}

    context.assertIsWireableTrait(isInFactoryConstructor = false)

    val lamParams = {
      val byNameMethodArgs = methodDecls.map {
        case MemberRepr(n, _, maybeSym, t, _) => ParamRepr(s"_$n", maybeSym, util.ensureByName(util.returnTypeOfMethodOrByName(t)))
      }
      flatCtorParams ++ byNameMethodArgs
    }

    val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        context.implementTraitAutoImplBody(lamSym, lamOnlyCtorArguments, lamOnlyMethodArguments)
    }

//    locally {
//      given Printer[Tree] = Printer.TreeStructure
//      val resultTpeSym = context.resultTpeSyms.head
//
//      report.warning(
//        s"""|tpe = ${context.resultTpe}
//            |symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
//            |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//            |fields = ${resultTpeSym.fieldMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//            |methodsDecls = $methodDecls
//            |lamParams = $lamParams
//            |refinementMethods = ${context.refinementMethods}
//            |tree = ${resultTpeSym.tree}
//            |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
//            |pct  = ${resultTpeSym.primaryConstructor.tree}
//            |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
//            |pctt = ${context.resultTpe.memberType(resultTpeSym.primaryConstructor)}
//            |pcts = ${context.resultTpe.baseClasses
//             .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
//             .mkString("\n")}
//            |defn = ${resultTpeSym.tree.show}
//            |lam  = ${lamExpr.asTerm}
//            |lam  = ${lamExpr.show}
//            |""".stripMargin
//      )
//    }

    val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.Trait })
    '{ new TraitConstructor[R](${ f }) }
  }

}
