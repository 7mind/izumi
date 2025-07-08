package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object DischargeDummyMacro {
  inline def dischargeDummy[I, N](inline function: N ?=> Functoid[I]): Functoid[I] = ${ dischargeDummyImpl[I, N]('function) }

  def dischargeDummyImpl[I: Type, N: Type](
    function: Expr[N ?=> Functoid[I]]
  )(using qctx: Quotes
  ): Expr[Functoid[I]] = {
    import qctx.reflect.*
    val dummyTypeSymbol: Symbol = TypeRepr.of[FunctoidDummyImplicit].typeSymbol
    val functionTerm = function.asTerm
    @tailrec
    def rewriteDummies(fun: Term): Expr[Functoid[I]] = {
      fun match {
        case block @ Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _: Closure) =>
          def extractDummyArguments(body: Term): List[Term] = {
            val treeAccumulator: TreeAccumulator[Set[Term]] = new TreeAccumulator[Set[Term]] {
              override def foldTree(x: Set[Term], tree: Tree)(owner: Symbol): Set[Term] = {
                tree match {
                  case i: Ident => if (i.tpe.baseClasses.contains(dummyTypeSymbol)) x + i else x
                  case _ => foldOverTree(x, tree)(owner)
                }
              }
            }
            treeAccumulator.foldTree(Set.empty, body)(Symbol.spliceOwner).toList
          }

          def flattenIntersectionType(t: TypeRepr): List[TypeRepr] = {
            def loop(t: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = {
              t match {
                case and: AndType => loop(and.left, acc) ++ loop(and.right, acc)
                case t => acc.appended(t)
              }
            }
            loop(t, Nil)
          }
          
          def rewriteDummiesByIdent(
            tree: Tree,
            dummyArgs: Map[Term, Term], // dummy -> real
            owner: Symbol,
          ): Tree = {
            val treeMap: TreeMap = new TreeMap {
              override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = {
                tree match {
                  case i: Ident => dummyArgs.getOrElse(i, i)
                  case _ => super.transformTerm(tree)(owner)
                }
              }
            }
            treeMap.transformTree(tree)(owner)
          }

          val dummyTerms = extractDummyArguments(body)
          if (dummyTerms.nonEmpty) {
            println("discharge block: " + block.show)
            val dummyParam = singleParamList.params.head
            val dummyParamSymbol = dummyParam.symbol
            val dummyParamTypes = flattenIntersectionType(dummyParam.tpt.tpe).drop(1)

            val lambdaArgsNames = dummyTerms.map(_.symbol.name)
            val lambdaArgsTypes = dummyParamTypes

            val methodType = MethodType(lambdaArgsNames)(
              _ => lambdaArgsTypes,
              _ => body.tpe,
            )

            val lambda = Lambda(
              Symbol.spliceOwner,
              methodType,
              (owner, args) => {
                val byDummy = dummyTerms.zip(args.map(_.asExpr.asTerm)).toMap
                rewriteDummiesByIdent(body, byDummy, owner)
              },
            )

            val implicits = dummyParamTypes.map(
              tpe =>
                tpe.asType match {
                  case '[a] => Expr.summonIgnoring[a](dummyParamSymbol).map(_.asTerm).getOrElse(report.errorAndAbort(s"Failed to perform an implicit search for "))
                  case t => report.errorAndAbort(s"Failed to perform an implicit search for $t")
                }
            )

            val anyTpe = TypeRepr.of[Any]
            val fnType = defn.FunctionClass(implicits.size).typeRef.appliedTo(List.fill(implicits.size + 1)(anyTpe))
            val res = Select.unique(lambda, "apply").appliedToArgs(implicits).asExprOf[Functoid[I]]
            println("discharge result: " + res.show)
            res
          } else '{ $function(using null.asInstanceOf[N]) }
          
        case Typed(term, _) => rewriteDummies(term)
        case Inlined(_, _, term) => rewriteDummies(term)
        case Block(List(), term) => rewriteDummies(term)
        case _ =>
          val term = fun.asExprOf[N ?=> Functoid[I]]
          '{ $term(using null.asInstanceOf[N]) }
      }
    }

    rewriteDummies(function.asTerm)
  }
}
