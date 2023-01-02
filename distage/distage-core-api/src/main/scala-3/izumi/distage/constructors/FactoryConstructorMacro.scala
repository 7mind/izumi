package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}
import scala.util.control.NonFatal

object FactoryConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[FactoryConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    import util.{ParamRepr, ParamReprLists, factoryUtil}
    import factoryUtil.{FactoryProductData, InjectedDependencyParameter, MethodParameter}
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "FactoryConstructor")

    val factoryContext = new ConstructorContext[R, qctx.type, util.type](util)
    import factoryContext.{resultTpe, resultTpeSyms, resultTpeTree}

    if (!factoryContext.isFactory) {
      report.errorAndAbort(
        s"""${resultTpeSyms.mkString(" & ")} has no abstract methods so it's not a factory;; methods=${resultTpeSyms.map(s => (s, s.methodMembers))};; tpeTree=$resultTpeTree;; tpeTreeClass=${resultTpeTree.getClass}""".stripMargin
      )
    }

    val refinementNames = factoryContext.refinementMethods.iterator.map(_._1).toSet

    def generateDecls(cls: Symbol): List[Symbol] = factoryContext.methodDecls.map {
      case (name, _, _, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        val overrideFlag = if (!refinementNames.contains(name)) Flags.Override else Flags.EmptyFlags
        Symbol.newMethod(cls, name, mtype, overrideFlag | Flags.Method, Symbol.noSymbol)
    }

    var flatLambdaSigIndex = 0 // index of a new dependency to add to the outermost lambda requesting parameters

    val factoryProductData = factoryContext.methodDecls.map {
      (n, _, mbMethodSym, methodType) =>
        factoryUtil.getFactoryProductData(resultTpe) {
          () =>
            val curIndex = flatLambdaSigIndex
            flatLambdaSigIndex += 1
            curIndex
        }(n, mbMethodSym, methodType)
    }

    val ctorArgs = factoryContext.flatCtorParams
    val byNameMethodArgs = factoryProductData.flatMap(_.byNameDependencies)
    val lamParams: List[ParamRepr] = ctorArgs ++ byNameMethodArgs
    val indexShift = ctorArgs.length

    val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
      (lamSym, lambdaArgs) =>

        val (lamOnlyCtorArguments, _) = lambdaArgs.splitAt(factoryContext.flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(factoryContext.constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSyms.map(_.name).mkString("With")}FactoryAutoImpl"
        var methodSymbols: List[Symbol] = null
        val clsSym = Symbol.newClass(
          parent = lamSym,
          name = name,
          parents = factoryContext.parentTypesParameterized,
          decls = {
            s =>
              val methods = generateDecls(s)
              methodSymbols = methods
              methods
          },
          selfType = None,
        )

        val defs = factoryProductData.zip(methodSymbols).map {
          case (factoryProductData, methodSym) =>
            factoryUtil.implementFactoryMethod(lambdaArgs, factoryProductData, methodSym, indexShift)
        }

        val clsDef = ClassDef(clsSym, parents.toList, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTpeTree)
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTpeTree)
    }

//    report.warning(
//      s"""|symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
//          |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//          |tree = ${resultTpeSym.tree}
//          |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
//          |pct  = ${resultTpeSym.primaryConstructor.tree}
//          |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
//          |pctt = ${resultTpeSym.typeRef.memberType(resultTpeSym.primaryConstructor)}
//          |pcts = ${resultTpeSym.typeRef.baseClasses
//           .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
//           .mkString("\n")}
//          |defn = ${resultTpeSym.tree.show}
//          |lam  = ${lamExpr.asTerm}
//          |lam  = ${lamExpr.show}
//          |prms = ${methodDecls.map((n, t) => (s"_$n", t, t.getClass))}
//          |""".stripMargin
//    )

    val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.Factory })
    '{ new FactoryConstructor[R](${ f }) }

  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case NonFatal(t) => qctx.reflect.report.errorAndAbort(t.stackTrace) }
//  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
