package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.reflection.ReflectiveCall

import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}
import scala.util.control.NonFatal

object FactoryConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[FactoryConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    import util.{MemberRepr, ParamRepr, ParamReprLists, factoryUtil}
    import factoryUtil.{FactoryProductData, InjectedDependencyParameter, MethodParameter}
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "FactoryConstructor")

    val factoryContext = new ConstructorContext[R, qctx.type, util.type](util)
    import factoryContext.{resultTpe, resultTpeSyms, resultTpeTree}

    if (!factoryContext.isFactoryOrTrait) {
      report.errorAndAbort(
        s"""${resultTpeSyms.mkString(" & ")} has no abstract methods so it's not a factory;; methods=${resultTpeSyms.map(
            s => (s, s.methodMembers)
          )};; tpeTree=$resultTpeTree;; tpeTreeClass=${resultTpeTree.getClass}""".stripMargin
      )
    }

    var flatLambdaSigIndex = 0 // index of a new dependency to add to the outermost lambda requesting parameters

    val factoryProductData = factoryContext.methodDecls.map {
      case MemberRepr(n, _, mbMethodSym, methodType, _) =>
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
        val clsSym = {
          // def newClass(parent: Symbol, name: String, parents: List[TypeRepr], decls: Symbol => List[Symbol], selfType: Option[TypeRepr]): Symbol
          ReflectiveCall.call[Symbol](
            Symbol,
            "newClass",
            lamSym,
            name,
            factoryContext.parentTypesParameterized,
            {
              (s: Symbol) =>
                val methodSyms = factoryContext.methodDecls.generateDeclSymbols(s)
                methodSymbols = methodSyms
                methodSyms
            },
            None,
          )
        }

        val defs = factoryProductData.zip(methodSymbols).map {
          case (factoryProductData, methodSym) =>
            factoryUtil.implementFactoryMethod(lambdaArgs, factoryProductData, methodSym, indexShift)
        }

        val clsDef = {
//          ClassDef(clsSym, parents.toList, body = defs)
          ReflectiveCall.call[ClassDef](ClassDef, "apply", clsSym, parents.toList, defs)
        }
        val newCls = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTpeTree)
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTpeTree)
    }

//    locally {
//      val resultTpeSym = resultTpeSyms.head
//      report.warning(
//        s"""|symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
//            |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
//            |tree = ${resultTpeSym.tree}
//            |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
//            |pct  = ${resultTpeSym.primaryConstructor.tree}
//            |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
//            |pctt = ${resultTpeSym.typeRef.memberType(resultTpeSym.primaryConstructor)}
//            |pcts = ${resultTpeSym.typeRef.baseClasses
//             .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
//             .mkString("\n")}
//            |defn = ${resultTpeSym.tree.show}
//            |lam  = ${lamExpr.asTerm}
//            |lam  = ${lamExpr.show}
//            |prms = ${factoryContext.methodDecls.map(m => (s"_${m.name}", m.tpe, m.tpe.getClass))}
//            |""".stripMargin
//      )
//    }

    val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.Factory })
    '{ new FactoryConstructor[R](${ f }) }

  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case NonFatal(t) => qctx.reflect.report.errorAndAbort(t.stacktraceString) }
//  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stacktraceString) }

}
