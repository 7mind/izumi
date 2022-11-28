package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.reflect.WeakTag

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object TraitConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[TraitConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    import util.ParamRepr
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "TraitConstructor")

    val context = new ConstructorContext[R, qctx.type, util.type](util)

    makeImpl[R](util, context)
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  @experimental
  def makeImpl[R: Type](using qctx: Quotes)(util: ConstructorUtil[qctx.type], context: ConstructorContext[R, qctx.type, util.type]): Expr[TraitConstructor[R]] = {
    import qctx.reflect.*
    import util.ParamRepr
    import context.*

    if (!context.isWireableTrait) {
      report.errorAndAbort(
        s"""$resultTpeSym has abstract methods taking parameters, expected only parameterless abstract methods:
           |  ${abstractMethodsWithParams.map(s => s.name -> s.flags.show)}
           |  [others: ${abstractMembers.map(s => s.name -> s.flags.show)}]""".stripMargin
      )
    }

    assert(methodDecls.map(_._1).size == methodDecls.size, "BUG: duplicated abstract method names")

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, isMethod, _, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        if (isMethod) {
          Symbol.newMethod(cls, name, mtype, Flags.Method | Flags.Override, Symbol.noSymbol)
        } else {
          Symbol.newVal(cls, name, mtype, Flags.Override, Symbol.noSymbol)
        }
    }

    // TODO: decopypaste
    val lamParams = {
      val ctorArgs = flatCtorParams.map { case ParamRepr(n, s, t) => ParamRepr(n, s, util.returnTypeOfMethod(t)) }
      val byNameMethodArgs = methodDecls.map { case (n, _, s, t) => ParamRepr(s"_$n", s, util.ensureByName(util.returnTypeOfMethodOrByName(t))) }
      ctorArgs ++ byNameMethodArgs
    }

    val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(resultTpe, constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSym.name}TraitAutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentTypesParameterized, decls = decls, selfType = None)

        val defs = methodDecls.zip(lamOnlyMethodArguments).map {
          case ((name, isMethod, _, _), arg) =>
            val methodSyms = if (isMethod) clsSym.declaredMethod(name) else List(clsSym.declaredField(name))
            assert(methodSyms.size == 1, "BUG: duplicated methods!")
            val methodSym = methodSyms.head
            if (isMethod) {
              DefDef(methodSym, _ => Some(arg))
            } else {
              ValDef(methodSym, Some(arg))
            }
        }

        val clsDef = ClassDef(clsSym, parents.toList, body = defs)
        val newCls = {
          val applyTree = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTpeTree)
          '{ TraitConstructor.wrapInitialization[R](${ applyTree.asExprOf[R] })(compiletime.summonInline[WeakTag[R]]) }.asTerm
        }
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTpeTree)
    }

    import Printer.TreeStructure
    report.warning(
      s"""|tpe = $resultTpe
          |symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
          |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
          |fields = ${resultTpeSym.fieldMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
          |tree = ${resultTpeSym.tree}
          |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
          |pct  = ${resultTpeSym.primaryConstructor.tree}
          |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
          |pctt = ${resultTpe.memberType(resultTpeSym.primaryConstructor)}
          |pcts = ${resultTpe.baseClasses
           .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
           .mkString("\n")}
          |defn = ${resultTpeSym.tree.show}
          |lam  = ${lamExpr.asTerm}
          |lam  = ${lamExpr.show}
          |""".stripMargin
    )

    val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.Trait })
    '{ new TraitConstructor[R](${ f }) }
  }

}
