package izumi.distage.reflection.macros

import izumi.distage.model.providers.AbstractFunctoid
import izumi.distage.model.reflection.*

import scala.annotation.tailrec
import scala.collection.immutable.{List, Seq}
import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

trait FunctoidMacroBase[Ftoid[+X] <: AbstractFunctoid[X, Ftoid]] {
  protected def generateFunctoid[R: Type](
    using qctx: Quotes
  )(paramDefs: List[Expr[LinkedParameter]],
    originalFun: Expr[AnyRef],
    ignoreDuringImplicitsSearch: List[qctx.reflect.Symbol],
  ): Expr[Ftoid[R]]

  protected final def generateRawFnCall(argsCount: Int, rawFn: Expr[Any], args: Expr[Seq[Any]])(using qctx: Quotes): Expr[Any] = {
    import qctx.reflect.*

    val params = List.tabulate(argsCount) {
      idx =>
        '{ $args(${ Expr(idx) }) }
    }

    val anyTpe = TypeRepr.of[Any]
    val fnType = defn.FunctionClass(argsCount).typeRef.appliedTo(List.fill(argsCount + 1)(anyTpe))

    val fnAny = fnType.asType match {
      case '[a] =>
        '{ ${ rawFn.asExprOf[Any] }.asInstanceOf[a] }
      case _ =>
        report.errorAndAbort(s"This is totally unexpected: ${fnType.show} type is higher-kinded type constructor, but expected a proper type")
    }

    Select.unique(fnAny.asTerm, "apply").appliedToArgs(params.map(_.asTerm)).asExprOf[Any]
  }

  final class FunctoidMacroImpl[Q <: Quotes](
    using val qctx: Q
  )(paramsMacro: FunctoidParametersMacroBase[qctx.type],
    dummyImplicitsExtractorMacro: DummyImplicitsExtractorMacro[qctx.type],
  ) {
    import qctx.reflect.*

    private val unignorableDummyTypeSymbol: Symbol = TypeRepr.of[IndiscriminateFunctoidDummyImplicit].typeSymbol

    def make[R: Type](fun: Expr[AnyRef]): Expr[Ftoid[R]] = {
      val (parameters, func, dummyImplicitSymbols) = analyze(fun.asTerm)
      val out = generateFunctoid[R](parameters, func, dummyImplicitSymbols)

      //      report.warning(
      //        s"""fun=${fun.show}
      //           |funType=${fun.asTerm.tpe}
      //           |funSym=${fun.asTerm.symbol}
      //           |funTypeSym=${fun.asTerm.tpe.typeSymbol}
      //           |funTypeSymBases=${fun.asTerm.tpe.baseClasses}
      //           |outputType=${Type.show[R]}
      //           |rawOutputType=(${TypeRepr.of[R]})
      //           |produced=${out.show}""".stripMargin
      //      )

      out
    }

    @tailrec def analyze(fun: Term): (List[Expr[LinkedParameter]], Expr[AnyRef], List[Symbol]) = fun match {
      case block @ Block(List(DefDef(_, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _: Closure) =>
        def copyArgsIntoBody(
          body: Term,
          noImplicitsProvided: Map[Term, Term],
          implicitsProvided: Map[Term, Option[Term]],
          argsOwner: Symbol,
        ): Tree = {
          val treeMap: TreeMap = new TreeMap {
            override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = {
              tree match {
                case i: Ident =>
                  noImplicitsProvided
                    .get(i)
                    .orElse(implicitsProvided.getOrElse(i, None))
                    .getOrElse(i)
                case _ => super.transformTerm(tree)(owner)
              }
            }
          }

          treeMap.transformTree(body)(argsOwner)
        }

        val dummyArgs = dummyImplicitsExtractorMacro.extractDummyArguments(body, Symbol.spliceOwner)
        val (noImplicitsProvided, implicitsProvided) = dummyArgs
          .map(
            dummy =>
              if (dummy.term.tpe.baseClasses.contains(unignorableDummyTypeSymbol)) {
                dummy
              } else {
                val maybeImplicit = Implicits.searchIgnoring(dummy.tpe)(dummy.term.symbol) match {
                  case s: ImplicitSearchSuccess => Some(s.tree)
                  case _ => None
                }
                dummy.withProvidedImplicit(maybeImplicit)
              }
          ).partition(_.providedImplicit.isEmpty)

        val ignoreDuringImplicitSearch = {
          val inTreeDummySyms = dummyArgs.iterator.map(_.term.symbol).distinct.toList
          dummyImplicitsExtractorMacro.extractDummySymbolsFromImplicitSearch(inTreeDummySyms)
        }

        if (dummyArgs.nonEmpty) {
          val newValDefs = noImplicitsProvided.map {
            dummy =>
              ValDef(
                Symbol.newVal(
                  Symbol.spliceOwner,
                  dummy.term.symbol.name,
                  dummy.tpe,
                  Flags.EmptyFlags,
                  Symbol.spliceOwner,
                ),
                None,
              )
          }
          val linkedParamsImplicits = analyzeLambdaOrMethodRef(TermParamClause(newValDefs), body, ignoreDuringImplicitSearch)(noImplicitsProvided.map(_.term.symbol))
          val linkedParamsRegular = analyzeLambdaOrMethodRef(singleParamList, body, ignoreDuringImplicitSearch)()
          val allLinkedParams = linkedParamsImplicits ++ linkedParamsRegular

          val implicitsNames = noImplicitsProvided.map(_.term.symbol.name)
          val regularNames = singleParamList.params.map(_.name)
          val lambdaArgsNames = implicitsNames ++ regularNames

          val implicitsTypes = noImplicitsProvided.map(_.tpe)
          val regularTypes = singleParamList.params.map(_.tpt.tpe)
          val lambdaArgsTypes = implicitsTypes ++ regularTypes

          val methodType = MethodType(lambdaArgsNames)(
            _ => lambdaArgsTypes,
            _ => body.tpe,
          )

          val resultLambda = Lambda(
            Symbol.spliceOwner,
            methodType,
            (owner, args) => {
              val argsWithNoImplicitsProvided = noImplicitsProvided.map(_.term).zip(args.map(_.asExpr.asTerm)).toMap
              val argsWithImplicitsProvided = implicitsProvided.map(d => d.term -> d.providedImplicit).toMap
              val newFun = copyArgsIntoBody(block, argsWithNoImplicitsProvided, argsWithImplicitsProvided, owner).asExpr.asTerm
              val params = args.takeRight(singleParamList.params.size).map(_.asExpr.asTerm)
              Select.unique(newFun, "apply").appliedToArgs(params)
            },
          )

          (allLinkedParams, resultLambda.asExprOf[AnyRef], ignoreDuringImplicitSearch)
        } else {
          (analyzeLambdaOrMethodRef(singleParamList, body, ignoreDuringImplicitSearch)(), fun.asExprOf[AnyRef], ignoreDuringImplicitSearch)
        }
      case Typed(term, _) => analyze(term)
      case Inlined(_, _, term) => analyze(term)
      case Block(List(), term) => analyze(term)
      case otherExpr => (analyzeTypeOfExpr(otherExpr), fun.asExprOf[AnyRef], Nil)
    }

    private def analyzeLambdaOrMethodRef(
      singleParamList: TermParamClause,
      body: Term,
      ignoreDuringImplicitsSearch: List[Symbol],
    )(symbolSearchList: List[Symbol] = singleParamList.params.map(_.symbol)
    ): List[Expr[LinkedParameter]] = {
      val methodRefParamSyms: List[Symbol] = {
        @tailrec
        def go(t: Tree, rOffset: Int): List[Symbol] = t match {
          case Apply(f, args) if args.map(_.symbol) == symbolSearchList =>
            f.symbol.paramSymss.filterNot(_.headOption.exists(_.isTypeParam)).dropRight(rOffset).lastOption.toList.flatten
          case Apply(f, _) => go(f, rOffset + 1)
          case Inlined(_, _, term) => go(term, rOffset)
          case Block(List(), term) => go(term, rOffset)
          case Typed(term, _) => go(term, rOffset)
          case _ => Nil
        }

        go(body, 0)
      }

      val annotationsOnMethodAreNonEmptyAndASuperset = {
        methodRefParamSyms.sizeCompare(singleParamList.params) == 0
        && methodRefParamSyms.exists(_.annotations.nonEmpty)
      }

//      System.err.println(
//        s"""l:${Position.ofMacroExpansion.startLine}, mrefparams = $methodRefParamSyms
//           |termclause = $singleParamList
//           |body=${body.show}
//           |sym=${body match { case Apply(f, _) => f.symbol -> f.symbol.paramSymss; case _ => None }}
//           |verdict=$annotationsOnMethodAreNonEmptyAndASuperset
//           |""".stripMargin
//      )

      // if method reference has more annotations, get parameters from reference instead
      // to preserve annotations!
      if (annotationsOnMethodAreNonEmptyAndASuperset) {
        // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved/unrelated
        // But lambda params should be sufficiently 'grounded' at this point
        // (Besides, lambda types are the ones specified by the caller, we should respect them)
        singleParamList.params.zip(methodRefParamSyms).map {
          case (ValDef(name, tpeTree, _), mSym) =>
            paramsMacro.makeParam(
              name = name,
              tpe = Left(tpeTree),
              mbSym = Some(mSym),
              annotSym = Some(mSym),
              annotTpe = Right(mSym.owner.typeRef.memberType(mSym)),
              ignoreDuringImplicitsSearch,
            )
        }
      } else {
        singleParamList.params.map {
          case valDef @ ValDef(name, tpeTree, _) =>
            val mbSym = Some(valDef.symbol).filterNot(_.isNoSymbol)
            paramsMacro.makeParam(
              name = name,
              tpe = Left(tpeTree),
              mbSym = mbSym,
              annotSym = mbSym,
              annotTpe = Left(tpeTree),
              ignoreDuringImplicitsSearch,
            )
        }
      }
    }

    private def analyzeTypeOfExpr(other: Term): List[Expr[LinkedParameter]] = {
      val rawTpe = other.underlying.tpe
      val functionTpe = rawTpe.baseClasses.find(_.fullName.startsWith("scala.Function")) match {
        case Some(fn) =>
          rawTpe.baseType(fn)
        case None =>
          report.errorAndAbort(s"Could not find scala.Function* base class for ${rawTpe.show} - not a function! baseClasses were: ${rawTpe.baseClasses}")
      }

      functionTpe.typeArgs match {
        case Nil => Nil
        case o =>
          val args = o.init
          args.iterator.zipWithIndex.map {
            (tpe, idx) =>
              paramsMacro.makeParam(s"arg_$idx", Right(tpe), None, None, Right(tpe))
          }.toList
      }
    }

  }

}
