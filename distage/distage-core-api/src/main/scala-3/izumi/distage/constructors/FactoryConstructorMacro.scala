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
    import util.{ParamRepr, ParamReprLists}
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "FactoryConstructor")

    val context = new ConstructorContext[R, qctx.type, util.type](util)
    import context.*

    if (!isFactory) {
      report.errorAndAbort(
        s"""$resultTpeSym has no abstract methods so it's not a factory;; methods=${resultTpeSym.methodMembers};; tpeTree=$resultTpeTree;; tpeTreeClass=${resultTpeTree.getClass}""".stripMargin
      )
    }

    val refinementNames = refinementMethods.iterator.map(_._1).toSet

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, _, _, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        val overrideFlag = if (!refinementNames.contains(name)) Flags.Override else Flags.EmptyFlags
        Symbol.newMethod(cls, name, mtype, overrideFlag | Flags.Method, Symbol.noSymbol)
    }

    sealed trait Parameter
    final case class DependencyParameter(/*paramName: String,*/ mbParamSymbol: Option[Symbol], paramTpe: TypeRepr, argName: String, flatLambdaSigIndex: Int)
      extends Parameter
    final case class MethodParameter(/*sigName: String, tpe: TypeRepr, */ flatLocalSigIndex: Int) extends Parameter

    final case class FactoryMethodData(
//      name: String,
      getFactoryProductType: List[TypeTree] => TypeRepr,
//      implTypeSym: Symbol,
      params: List[List[Parameter]],
    )

    var flatLambdaSigIndex = 0
    val factoryMethodData = methodDecls.map {
      (n, _, mbSym, methodType) =>
        val methodParams = util.extractMethodTermParamLists(methodType, mbSym.getOrElse(Symbol.noSymbol)).flatten
        val getFactoryProductType = {
          (args: List[TypeTree]) =>
            val rett0 = methodType match {
              case p: PolyType =>
                p.appliedTo(args.map(_.tpe))
              case _ =>
                methodType
            }
            val rett = util.returnTypeOfMethodOrByName(rett0)
            util.readWithAnnotation(rett).getOrElse(rett).dealias.simplified
        }
        val factoryProductType = getFactoryProductType(Nil)

        val isTrait = factoryProductType.typeSymbol.flags.is(Flags.Trait) || factoryProductType.typeSymbol.flags.is(Flags.Abstract)
        if (isTrait) {
//          report.errorAndAbort(
//            s"Cannot build factory for ${resultTpe.show}, factory method $n returns type ${impltype.show} which cannot be constructed with `new`"
//          )
          val msg = s"Cannot build factory for ${resultTpe.show}, factory method $n returns type ${factoryProductType.show} which cannot be constructed with `new`"
          return '{ (throw new RuntimeException(${ Expr(msg) })): FactoryConstructor[R] }
        }

        val factoryProductCtorParamLists = util.buildConstructorParameters(factoryProductType)

        util.assertSignatureIsAcceptableForFactory(methodParams, resultTpe, s"factory method $n")
        util.assertSignatureIsAcceptableForFactory(factoryProductCtorParamLists.flatten, resultTpe, s"implementation constructor ${factoryProductType.show}")

        val indexedMethodParams = methodParams.zipWithIndex
        val methodParamIndex = indexedMethodParams.map { case (ParamRepr(n, _, t), idx) => (t, (n, idx)) }

        val params = factoryProductCtorParamLists.zipWithIndex.map {
          case (params, paramListIdx) =>
            params.map {
              case ParamRepr(paramName, symbol, paramType) =>
                methodParamIndex.filter((t, _) => util.returnTypeOfMethodOrByName(t) =:= util.returnTypeOfMethodOrByName(paramType)) match {
                  case (_, (_, idx)) :: Nil =>
                    MethodParameter( /*paramName, paramType, */ idx)

                  case Nil =>
                    val curIndex = flatLambdaSigIndex
                    flatLambdaSigIndex += 1
                    val newName = if (paramListIdx > 0) {
                      s"_${n}_${paramListIdx}_$paramName"
                    } else {
                      s"_${n}_$paramName"
                    }
                    DependencyParameter( /*paramName, */ symbol, util.ensureByName(paramType), newName, curIndex)

                  case multiple =>
                    val (_, (_, idx)) = multiple
                      .find { case (_, (n, _)) => n == paramName }
                      .getOrElse(
                        report.errorAndAbort(
                          s"""Couldn't disambiguate between multiple arguments with the same type available for parameter $paramName: ${paramType.show} of ${factoryProductType.show} constructor
                             |Expected one of the arguments to be named `$paramName` or for the type to be unique among factory method arguments""".stripMargin
                        )
                      )
                    MethodParameter(idx)
                }
            }
        }

        val consumedSigParams = params.flatten.collect { case p: MethodParameter => p.flatLocalSigIndex }.toSet
        val unconsumedParameters = indexedMethodParams.filterNot(p => consumedSigParams.contains(p._2))

        if (unconsumedParameters.nonEmpty) {
          import izumi.fundamentals.platform.strings.IzString.*
          val explanation = unconsumedParameters.map { case (ParamRepr(n, _, t), _) => s"$n: ${t.show}" }.niceList()
          report.errorAndAbort(
            s"Cannot build factory for ${resultTpe.show}, factory method $n has arguments which were not consumed by implementation constructor ${factoryProductType.show}: $explanation"
          )
        }

        FactoryMethodData( /*n,*/ getFactoryProductType, /*factoryProductType.typeSymbol,*/ params)
    }

    val ctorArgs = flatCtorParams.map { case ParamRepr(n, s, t) => ParamRepr(n, s, util.returnTypeOfMethodOrByName(t)) }
    val byNameMethodArgs = factoryMethodData.flatMap(_.params).flatten.collect { case p: DependencyParameter => ParamRepr(p.argName, p.mbParamSymbol, p.paramTpe) }
    val lamParams: List[ParamRepr] = ctorArgs ++ byNameMethodArgs
    val indexShift = ctorArgs.length

    val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, _) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(resultTpe, constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSym.name}FactoryAutoImpl"
        var declSymbols: List[Symbol] = null
        val clsSym = Symbol.newClass(
          lamSym,
          name,
          parents = parentTypesParameterized,
          decls = {
            s =>
              val methods = decls(s)
              declSymbols = methods
              methods
          },
          selfType = None,
        )

        val defs = factoryMethodData.zip(declSymbols).map {
          case (FactoryMethodData(getFactoryProductType, params), methodSym) =>
            DefDef(
              methodSym,
              sigArgs => {

                val (sigFlat, methodTypeArgs) = sigArgs.flatten.partitionMap { case t: Term => Left(t); case t: TypeTree => Right(t) }
                val implType = getFactoryProductType(methodTypeArgs)
                val ctorTreeParameterized = util.buildConstructorApplication(implType)

                val argsLists: List[List[Term]] = params.map {
                  pl =>
                    pl.map {
                      case p: DependencyParameter =>
                        args0(p.flatLambdaSigIndex + indexShift)
                      case p: MethodParameter =>
                        sigFlat(p.flatLocalSigIndex)
                    }
                }

                // TODO: check that there are no unconsumed parameters

                val appl = argsLists.foldLeft(ctorTreeParameterized)(_.appliedToArgs(_))
                val trm = Typed(appl, TypeTree.of(using implType.asType))

                Some(trm)
              },
            )
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
