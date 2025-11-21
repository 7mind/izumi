package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object DischargeDummyMacro {
  inline def dischargeDummy[I, N <: FunctoidDummyImplicit](inline function: N ?=> Functoid[I]): Functoid[I] = ${ dischargeDummyImpl[I, N]('function) }

  def dischargeDummyImpl[I: Type, N: Type](
    originalFunction: Expr[N ?=> Functoid[I]]
  )(using qctx: Quotes
  ): Expr[Functoid[I]] = {
    import qctx.reflect.*

    val dummyImplicitsExtractorMacro = new DummyImplicitsExtractorMacro[qctx.type]

    @tailrec def rewrite(fun: Term): Expr[Functoid[I]] = {
      fun match {
        case Block(List(DefDef(_, (singleParamList: TermParamClause) :: Nil, _, Some(body))), _: Closure) =>
          val dummyArgs = dummyImplicitsExtractorMacro.extractDummyArguments(body, Symbol.spliceOwner)
          if (dummyArgs.nonEmpty) {
            val dummyParamSymbol = singleParamList.params.head.symbol
            val dummies = dummyArgs.iterator.map(d => d.term -> d.tpe).toMap
            recursivelyReplaceDummies(using qctx)(dummyImplicitsExtractorMacro, List(dummyParamSymbol))(body, Symbol.spliceOwner, dummies)
              .asExpr.asInstanceOf[Expr[Functoid[I]]]
          } else {
            body.asExpr.asInstanceOf[Expr[Functoid[I]]]
          }

        case Typed(term, _) => rewrite(term)
        case Inlined(_, _, term) => rewrite(term)
        case Block(List(), term) => rewrite(term)
        case _ =>
          '{ ${ originalFunction }(using null.asInstanceOf[N]) }
      }
    }

    rewrite(originalFunction.asTerm)
  }

  private def recursivelyReplaceDummies(
    using qctx: Quotes
  )(extractor: DummyImplicitsExtractorMacro[qctx.type],
    dummySyms: List[qctx.reflect.Symbol],
  )(expr: qctx.reflect.Term,
    owner: qctx.reflect.Symbol,
    dummies: Map[qctx.reflect.Term, qctx.reflect.TypeRepr],
  ): qctx.reflect.Term = {
    import qctx.reflect.*
    new TreeMap {
      override def transformTerm(tree: qctx.reflect.Term)(owner: qctx.reflect.Symbol): qctx.reflect.Term = tree match {
        case ident: Ident =>
          dummies.get(ident) match {
            case None =>
              ident
            case Some(tpe) =>
              Implicits.searchIgnoring(tpe)(dummySyms*) match {
                case f: ImplicitSearchFailure =>
                  qctx.reflect.report.errorAndAbort(s"Couldn't discharge dummy of type ${tpe.show} (${tpe.show(using Printer.TypeReprStructure)}): ${f.explanation}")
                case s: ImplicitSearchSuccess =>
                  val newDummies = extractor.extractDummyArguments(s.tree, owner).iterator.map(d => d.term -> d.tpe).toMap[Term, TypeRepr]
                  recursivelyReplaceDummies(using qctx)(extractor, dummySyms)(s.tree, owner, newDummies)
              } // .changeOwner(owner) // seems like that's unnecessary
          }
        case _ => super.transformTerm(tree)(owner)
      }
    }.transformTerm(expr)(owner)
  }

}
