package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object FactoryConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[FactoryConstructor[R]] = '{ ??? }

  @experimental
  def make0[R: Type](using qctx: Quotes): Expr[FactoryConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

    val util = new ConstructorUtil[qctx.type]()
    import util.ParamListExt
    import util.{ParamListRepr, ParamListsRepr}

    val context = new ConstructorContext[R, qctx.type](util)
    import context.*

    if (!isFactory) {
      report.errorAndAbort(
        s"""$resultTpeSym has no abstract methods so it's not a factory;; methods=${resultTpeSym.methodMembers};; tpeTree=$resultTpeTree;; tpeTreeClass=${resultTpeTree.getClass}""".stripMargin
      )
    }

    val refinementNames = refinementMethods.map(_._1).toSet

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, _, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        val overrideFlag = if (!refinementNames.contains(name)) Flags.Override else Flags.EmptyFlags
        Symbol.newMethod(cls, name, mtype, overrideFlag | Flags.Method, Symbol.noSymbol)
    }

    sealed trait Parameter
    case class ProvidedParameter(constructorName: String, argName: String, tpe: TypeRepr, flatLamdaSigIndex: Int) extends Parameter
    case class SignatureParameter(sigName: String, tpe: TypeRepr, flatLocalSigIndex: Int) extends Parameter

    case class FactoryMethodDecl(
      name: String,
      impl: TypeRepr,
      implSymb: Symbol,
      params: List[List[Parameter]],
    )

    var flatLamdaSigIndex = 0
    val factoryMethodData = methodDecls.map {
      (n, _, t) =>
        val signatureParams = util.flattenMethodSignature(t)
        val rett = util.dropWrappers(util.realReturnType(t))
        val impltype = util.readWithAnno(rett).getOrElse(rett).dealias.simplified

        if (impltype.typeSymbol.flags.is(Flags.Trait) || impltype.typeSymbol.flags.is(Flags.Abstract)) {
          report.errorAndAbort(
            s"Cannot build factory for ${resultTpe.show}, factory method $n returns type ${impltype.show} which cannot be constructed with `new`"
          )
        }

        val constructorParamLists = util.buildConstructorParameters(impltype)

        util.assertSignatureIsAcceptableForFactory(signatureParams, resultTpe, s"factory method $n")
        util.assertSignatureIsAcceptableForFactory(constructorParamLists._2.flatten, resultTpe, s"implementation constructor ${impltype.show}")

        val indexedSigParams = signatureParams.zipWithIndex
        val sigRevIndex = indexedSigParams.map { case ((n, t), i) => (t, (n, i)) }.toMap

        val params = constructorParamLists._2.zipWithIndex.map {
          case (pl, listIdx) =>
            pl.map {
              case (pn, pt) =>
                sigRevIndex.find((t, _) => util.dropWrappers(t) =:= util.dropWrappers(pt)) match {
                  case Some((_, (_, i))) =>
                    SignatureParameter(pn, pt, i)

                  case None =>
                    val curIndex = flatLamdaSigIndex
                    flatLamdaSigIndex += 1
                    val newName = if (listIdx > 0) {
                      s"_${n}_${listIdx}_$pn"
                    } else {
                      s"_${n}_$pn"
                    }
                    ProvidedParameter(pn, newName, util.ensureByName(pt), curIndex)
                }
            }
        }

        val consumedSigParams = params.flatten.collect { case p: SignatureParameter => p.flatLocalSigIndex }.toSet
        val unconsumedParameters = indexedSigParams.filterNot(p => consumedSigParams.contains(p._2))

        if (unconsumedParameters.nonEmpty) {
          import izumi.fundamentals.platform.strings.IzString.*
          val explanation = unconsumedParameters.map { case ((n, t), _) => s"$n: ${t.show}" }.niceList()
          report.errorAndAbort(
            s"Cannot build factory for ${resultTpe.show}, factory method $n has arguments which were not consumed by implementation constructor ${impltype.show}: $explanation"
          )
        }

        FactoryMethodDecl(n, impltype, impltype.typeSymbol, params)
    }

    val ctorArgs = flatCtorParams.map((n, t) => (n, util.dropMethodType(t)))
    val byNameMethodArgs = factoryMethodData.flatMap(_.params).flatten.collect { case p: ProvidedParameter => (p.argName, p.tpe) }
    val lamParams: util.ParamListTree = (ctorArgs ++ byNameMethodArgs).toTrees
    val indexShift = ctorArgs.length

    val lamExpr = util.wrapIntoLambda[R](List(lamParams)) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, _) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(resultTpe, constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSym.name}FactoryAutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentTypesParameterized, decls = decls, selfType = None)

        val defs = factoryMethodData.map {
          fmd =>
            val methodSyms = clsSym.declaredMethod(fmd.name)
            assert(methodSyms.size == 1, "BUG: duplicated methods!")
            val methodSym = methodSyms.head

            DefDef(
              methodSym,
              sigArgs => {

                val ctorTreeParameterized = util.buildConstructorApplication(fmd.impl)
                val sigFlat = sigArgs.flatten

                val argsLists: List[List[Term]] = fmd.params.map {
                  pl =>
                    pl.map {
                      case p: ProvidedParameter =>
                        args0(p.flatLamdaSigIndex + indexShift)
                      case p: SignatureParameter =>
                        sigFlat(p.flatLocalSigIndex).asExpr.asTerm
                    }
                }

                // TODO: check that there are no unconsumed parameters
                val appl = argsLists.foldLeft(ctorTreeParameterized)(_.appliedToArgs(_))
                val trm = Typed(appl, TypeTree.of(using fmd.impl.asType))

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

    val f = functoidMacro.make[R](lamExpr)
    '{ new FactoryConstructor[R](${ f }) }

  } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
