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

    val functionTerm = function.asTerm
    @tailrec
    def rewriteDummies(fun: Term): Expr[Functoid[I]] = {
      fun match {
        case block @ Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _: Closure) =>
          def flattenIntersectionType(t: TypeRepr): List[TypeRepr] = {
            def loop(t: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = {
              t match {
                case and: AndType => loop(and.left, acc) ++ loop(and.right, acc)
                case t => acc.appended(t)
              }
            }
            loop(t, Nil)
          }

          val dummyParam = singleParamList.params.head
          val dummyParamSymbol = dummyParam.symbol
          val dummyParamTypes = flattenIntersectionType(dummyParam.tpt.tpe).drop(1).toIndexedSeq
          val treeMap: TreeMap = new TreeMap {
            private val dummyTypeSymbol: Symbol = TypeRepr.of[FunctoidDummyImplicit].typeSymbol
            private var counter: Int = 0
            override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = {
              tree match {
                case i: Ident =>
                  if (i.tpe.baseClasses.contains(dummyTypeSymbol)) {
                    val newIdent = dummyParamTypes(counter).asType match {
                      case '[a] => Expr.summonIgnoring[a](dummyParamSymbol).map(_.asTerm).getOrElse(i)
                      case t => report.errorAndAbort(s"Failed to perform an implicit search for $t")
                    }
                    counter += 1
                    newIdent
                  } else i
                case _ => super.transformTerm(tree)(owner)
              }
            }
          }
          treeMap.transformTree(body)(Symbol.spliceOwner).asExprOf[Functoid[I]]
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
