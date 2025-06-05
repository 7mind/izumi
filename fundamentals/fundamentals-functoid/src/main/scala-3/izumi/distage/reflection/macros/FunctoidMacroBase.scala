package izumi.distage.reflection.macros

import izumi.distage.model.providers.AbstractFunctoid
import izumi.distage.model.reflection.*

import scala.annotation.tailrec
import scala.collection.immutable.{List, ListMap, Seq}
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
      val (parameters, func) = analyze(fun.asTerm)
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

    @tailrec def analyze(fun: Term): (List[Expr[LinkedParameter]], Expr[AnyRef]) = fun match {
      case block @ Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), c @ Closure(_, _)) =>
        def inspectBody(body: Term): (List[(Term, TypeRepr)], List[(Term, TypeRepr)]) = {
          val treeAccumulator = new TreeAccumulator[List[(Term, TypeRepr, Boolean)]] {
            private val paramsBySymbol = singleParamList.params.map(_.symbol).toSet
            private def getArgsInfo(args: List[Term], types: List[TypeRepr], dummy: Boolean): List[(Term, TypeRepr, Boolean)] = {
              if (dummy) {
                args.zip(types).map { case (arg, tpe) => (arg, tpe, true) }
              } else {
                args.map(arg => (arg, arg.tpe, false))
              }
            }

            private def hasDummy(args: List[Term]): Boolean = {
              args.exists(_.tpe.baseClasses.contains(dummyTypeSymbol))
            }

            override def foldTree(
              x: List[(Term, TypeRepr, Boolean)],
              tree: Tree,
            )(owner: Symbol
            ): List[(Term, TypeRepr, Boolean)] = {
              tree match {
                case fun @ Apply(inner: Apply, args) =>
                  fun.fun.tpe match {
                    case lt: MethodType =>
                      val newTypes = getArgsInfo(args, lt.paramTypes, hasDummy(args))
                      foldTree(newTypes ++ x, inner)(owner)
                  }
                case fun @ Apply(s: Select, args) => foldOverTree(x, fun)(owner)
                case s: Select => foldOverTree(x, s)(owner)
                case i: Ident => if (paramsBySymbol.contains(i.symbol)) x.appended((i, i.tpe, false)) else x
                case fun @ Apply(TypeApply(_, argsTypes), args) =>
                  val newTypes = getArgsInfo(args, argsTypes.map(_.tpe), hasDummy(args))
                  newTypes ++ x
                case fun @ Apply(_, args) =>
                  fun.tpe match {
                    case lt: MethodType =>
                      val newTypes = getArgsInfo(args, lt.paramTypes, hasDummy(args))
                      newTypes ++ x
                    case _ => args.flatMap(arg => foldTree(x, arg)(owner)) ++ x
                  }

                case _ => foldOverTree(x, tree)(owner)
              }
            }
          }

          val args = treeAccumulator.foldTree(Nil, body)(Symbol.spliceOwner).distinct
          val dummy = args.filter(_._3).map(e => e._1 -> e._2)
          val nonDummy = args.filterNot(_._3).map(e => e._1 -> e._2)
          (dummy, nonDummy)
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

        val (dummyArgs, nonDummy) = inspectBody(body)
        val args = dummyArgs ++ nonDummy
        if (dummyArgs.nonEmpty) {
          val newValDefs = dummyArgs.map {
            case (term, tpe) =>
              ValDef(
                Symbol.newVal(
                  Symbol.spliceOwner,
                  term.symbol.name,
                  tpe,
                  Flags.EmptyFlags,
                  Symbol.spliceOwner,
                ),
                None,
              )
          }
          val linkedParamsImplicits = analyzeLambdaOrMethodRef(name, TermParamClause(newValDefs), body)
          val linkedParamsRegular = analyzeLambdaOrMethodRef(name, singleParamList, body)

          val lambdaArgsNames = args.map(_._1.symbol.name)
          val methodType = MethodType(lambdaArgsNames)(
            _ => args.map(_._2),
            _ => body.tpe,
          )
          val resultLambda = Lambda(
            Symbol.spliceOwner,
            methodType,
            (owner, args) => {
              val lambdaArgsByOldArgs = dummyArgs.map(_._1).zip(args.take(dummyArgs.size).map(_.asExpr.asTerm)).toMap
              val newFun = copyArgsIntoBody(block, lambdaArgsByOldArgs, owner).asExpr.asTerm
              val params = args.takeRight(nonDummy.size).map(_.asExpr.asTerm)
              val anyTpe = TypeRepr.of[Any]
              val fnType = defn.FunctionClass(args.size).typeRef.appliedTo(List.fill(args.size + 1)(anyTpe))
              Select.unique(newFun, "apply").appliedToArgs(params)
            },
          )
          println("result: " + resultLambda.show)

          (linkedParamsImplicits ++ linkedParamsRegular) -> resultLambda.asExprOf[AnyRef]
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
