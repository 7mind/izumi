package izumi.distage.reflection.macros

import izumi.distage.model.providers.AbstractFunctoid
import izumi.distage.model.reflection.*

import scala.annotation.{experimental, tailrec}
import scala.collection.immutable.{List, Seq}
import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

trait FunctoidMacroBase[Ftoid[+X] <: AbstractFunctoid[X, Ftoid]] {
  protected def generateFunctoid[R: Type, Q <: Quotes](paramDefs: List[Expr[LinkedParameter]], originalFun: Expr[AnyRef])(using qctx: Q): Expr[Ftoid[R]]

  protected final def generateRawFnCall[Q <: Quotes](argsCount: Int, rawFn: Expr[Any], args: Expr[Seq[Any]])(using qctx: Q): Expr[Any] = {
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

  final class FunctoidMacroImpl[Q <: Quotes](using val qctx: Q)(val paramsMacro: FunctoidParametersMacroBase[qctx.type]) {
    import qctx.reflect.*

    private val dummyType: TypeRepr = TypeRepr.of[Scala3FunctoidDummyImplicit]
    private val dummyTypeSymbol: Symbol = TypeRepr.of[Scala3FunctoidDummyImplicit].typeSymbol

    def make[R: Type](fun: Expr[AnyRef]): Expr[Ftoid[R]] = {
      val (parameters, func) = analyze[R](fun.asTerm)
      val out = generateFunctoid[R, Q](parameters, func)

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

    @tailrec def analyze[R: Type](fun: Term): (List[Expr[LinkedParameter]], Expr[AnyRef]) = fun match {
      case block @ Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _:Closure) =>
        final case class DummyArg(
          term: Term,
          tpe: TypeRepr,
          updated: Boolean,
        ) {
          def notUpdated: Boolean = !updated
        }

        def inspectBody(body: Term): List[DummyArg] = {
          val treeAccumulator: TreeAccumulator[Set[DummyArg]] = new TreeAccumulator[Set[DummyArg]] {
            private val paramsBySymbol = singleParamList.params.map(_.symbol).toSet
            private def update(args: Set[DummyArg], types: List[TypeRepr], dummy: Boolean): Set[DummyArg] = {
              if (dummy) {
                args.zip(types).map { case (arg, tpe) => arg.copy(tpe = tpe, updated = true) }
              } else Set.empty
            }

            private def hasDummy(args: List[Term]): Boolean = {
              args.exists(_.tpe.baseClasses.contains(dummyTypeSymbol))
            }

            override def foldTree(
              x: Set[DummyArg],
              tree: Tree,
            )(owner: Symbol
            ): Set[DummyArg] = {
              // println("entered fold tree: " + tree.show(using Printer.TreeStructure))
              // println("entered fold tree with x: " + x)
              tree match {
                case fun @ Apply(inner: Apply, args) =>
                  fun.fun.tpe match {
                    case lt: MethodType =>
                      val extracted = foldTrees(Set.empty, args)(owner)
                      val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                      // println("apply nested new types: " + newTypes)
                      foldTree(newTypes ++ x, inner)(owner)
                  }
                case fun @ Apply(s: Select, args) => foldOverTree(x, fun)(owner)
                case s: Select => foldOverTree(x, s)(owner)
                case i: Ident =>
                  if (i.tpe.baseClasses.contains(dummyTypeSymbol)) {
                    x + DummyArg(i, i.tpe, false)
                  } else x
                case fun @ Apply(t: TypeApply, args) =>
                  fun.fun.tpe match {
                    case lt: MethodType =>
                      val fromArgs = foldTrees(Set.empty, args)(owner)
                      val fromTerm = foldTree(Set.empty, t)(owner)
                      val newTypesFromArgs =
                        if (fromArgs.exists(_.notUpdated)) update(fromArgs, lt.paramTypes, true)
                        else fromArgs
                      val newTypesFromTerm =
                        if (fromTerm.exists(_.notUpdated)) update(fromTerm, lt.paramTypes, true)
                        else fromTerm
                      // println("typed new: " + (newTypesFromTerm ++ newTypesFromArgs))
                      newTypesFromTerm ++ newTypesFromArgs ++ x
                    case _ => foldTrees(x, args)(owner) ++ x
                  }

                case fun @ Apply(_, args) =>
                  fun.tpe match {
                    case lt: MethodType =>
                      val extracted = foldTrees(Set.empty, args)(owner)
                      val newTypes = update(extracted, lt.paramTypes, extracted.nonEmpty)
                      // println("inner new: " + newTypes)
                      newTypes ++ x
                    case _ => foldTrees(x, args)(owner) ++ x
                  }

                case _ => foldOverTree(x, tree)(owner)
              }
            }
          }

          treeAccumulator.foldTree(Set.empty, body)(Symbol.spliceOwner).toList
        }

        def copyArgsIntoBody(body: Term, lambdaArgsByOldArgs: Map[Term, Term], argsOwner: Symbol): Tree = {
          val treeMap: TreeMap = new TreeMap {
            private var counter = 0
            override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = {
              tree match {
                case i: Ident => lambdaArgsByOldArgs.getOrElse(i, i)
                case _ => super.transformTerm(tree)(owner)
              }
            }
          }

          treeMap.transformTree(body)(argsOwner)
        }

         //println("original block: " + block.show)
        val dummyArgs = inspectBody(body)
        // println("dummy args: " + dummyArgs)
        if (dummyArgs.nonEmpty) {
          val newValDefs = dummyArgs.map {
            a =>
              ValDef(
                Symbol.newVal(
                  Symbol.spliceOwner,
                  a.term.symbol.name,
                  a.tpe,
                  Flags.EmptyFlags,
                  Symbol.spliceOwner,
                ),
                None,
              )
          }
          val linkedParamsImplicits = analyzeLambdaOrMethodRef(name, TermParamClause(newValDefs), body)
          val linkedParamsRegular = analyzeLambdaOrMethodRef(name, singleParamList, body)
          val allLinkedParams = linkedParamsImplicits ++ linkedParamsRegular

          val implicitsNames = dummyArgs.map(_.term.symbol.name)
          val regularNames = singleParamList.params.map(_.name)
          val lambdaArgsNames = implicitsNames ++ regularNames

          val implicitsTypes = dummyArgs.map(_.tpe)
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
              val lambdaArgsByOldArgs = dummyArgs.map(_._1).zip(args.take(dummyArgs.size).map(_.asExpr.asTerm)).toMap
              val newFun = copyArgsIntoBody(block, lambdaArgsByOldArgs, owner).asExpr.asTerm
              val params = args.takeRight(singleParamList.params.size).map(_.asExpr.asTerm)
              val anyTpe = TypeRepr.of[Any]
              val fnType = defn.FunctionClass(args.size).typeRef.appliedTo(List.fill(args.size + 1)(anyTpe))
              Select.unique(newFun, "apply").appliedToArgs(params)
            },
          )

           //println("result: " + resultLambda.show)

          allLinkedParams -> resultLambda.asExprOf[AnyRef]
        } else {
          analyzeLambdaOrMethodRef(name, singleParamList, body) -> fun.asExprOf[AnyRef]
        }
      case Typed(term, _) => analyze(term)
      case Inlined(_, _, term) => analyze(term)
      case Block(List(), term) => analyze(term)
      case otherExpr => analyzeTypeOfExpr(otherExpr) -> fun.asExprOf[AnyRef]
    }

    private def analyzeLambdaOrMethodRef(name: String, singleParamList: TermParamClause, body: Term): List[Expr[LinkedParameter]] = {
      val methodRefParams = {
        @tailrec
        def go(t: Tree): List[Symbol] = t match {
          case Apply(f, args) if args.map(_.symbol) == singleParamList.params.map(_.symbol) =>
            f.symbol.paramSymss.filterNot(_.headOption.exists(_.isTypeParam)).flatten
          case Inlined(_, _, term) => go(term)
          case Block(List(), term) => go(term)
          case Typed(term, _) => go(term)
          case _ => Nil
        }

        go(body)
      }

      val annotationsOnMethodAreNonEmptyAndASuperset = {
        methodRefParams.sizeCompare(singleParamList.params) == 0
        && methodRefParams.exists(_.annotations.nonEmpty)
      }

      //      report.info(
      //        s"""mrefparams = $methodRefParams
      //           |termclause = $singleParamList
      //           |body=$body
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
        singleParamList.params.zip(methodRefParams).map {
          case (ValDef(name, tpeTree, _), mSym) =>
            paramsMacro.makeParam(
              name = name,
              tpe = Left(tpeTree),
              mbSym = Some(mSym),
              annotSym = Some(mSym),
              annotTpe = Right(mSym.owner.typeRef.memberType(mSym)),
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
