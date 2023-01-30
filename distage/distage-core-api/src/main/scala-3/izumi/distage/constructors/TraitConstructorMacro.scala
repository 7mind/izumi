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
    import util.{MemberRepr, ParamRepr}
    import context.{constructorParamLists, flatCtorParams, methodDecls, parentTypesParameterized, resultTpe, resultTpeSyms, resultTpeTree}

//    val resultTpeSym = resultTpeSyms.head

    if (!context.isWireableTrait) {
      report.errorAndAbort(
        s"""${resultTpeSyms.mkString(" & ")} has abstract methods taking parameters, expected only parameterless abstract methods:
           |  ${context.abstractMethodsWithParams.map(s => s.name -> s.flags.show)}
           |[methods without parameters: ${context.abstractMembers.map(s => s.name -> s.flags.show)}]""".stripMargin
      )
    }

    def generateDecls(cls: Symbol): List[Symbol] = methodDecls.map {
      case MemberRepr(name, isMethod, _, mtype, isNewMethod) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        val overrideFlag = if (!isNewMethod) Flags.Override else Flags.EmptyFlags
        if (isMethod) {
          Symbol.newMethod(cls, name, mtype, Flags.Method | overrideFlag, Symbol.noSymbol)
        } else {
          Symbol.newVal(cls, name, util.returnTypeOfMethodOrByName(mtype), overrideFlag, Symbol.noSymbol)
        }
    }

    // TODO: decopypaste
    val lamParams = {
      val byNameMethodArgs = methodDecls.map {
        case MemberRepr(n, _, maybeSym, t, _) => ParamRepr(s"_$n", maybeSym, util.ensureByName(util.returnTypeOfMethodOrByName(t)))
      }
      flatCtorParams ++ byNameMethodArgs
    }

    val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSyms.map(_.name).mkString("With")}TraitAutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentTypesParameterized, decls = generateDecls, selfType = None)

        val defs = methodDecls.zip(lamOnlyMethodArguments).map {
          case (MemberRepr(name, isMethod, _, _, _), arg) =>
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
        val applyNewTree = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTpeTree)
        val traitCtorTree = '{ TraitConstructor.wrapInitialization[R](${ applyNewTree.asExprOf[R] })(compiletime.summonInline[WeakTag[R]]) }.asTerm
        val block = Block(List(clsDef), traitCtorTree)
        Typed(block, resultTpeTree)
    }

    {
      given Printer[Tree] = Printer.TreeStructure
      val resultTpeSym = resultTpeSyms.head
      
//      report.warning(
//        s"""|tpe = $resultTpe
//            |symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
//            |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//            |fields = ${resultTpeSym.fieldMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//            |methodsDecls = $methodDecls
//            |refinementMethods = ${context.refinementMethods}
//            |tree = ${resultTpeSym.tree}
//            |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
//            |pct  = ${resultTpeSym.primaryConstructor.tree}
//            |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
//            |pctt = ${resultTpe.memberType(resultTpeSym.primaryConstructor)}
//            |pcts = ${resultTpe.baseClasses
//             .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
//             .mkString("\n")}
//            |defn = ${resultTpeSym.tree.show}
//            |lam  = ${lamExpr.asTerm}
//            |lam  = ${lamExpr.show}
//            |""".stripMargin
//      )
    }

    val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.Trait })
    '{ new TraitConstructor[R](${ f }) }
  }

}
