package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.reflection.ReflectionUtil
import izumi.reflect.Tag
import zio.ZEnvironment

import scala.collection.immutable.ArraySeq
import scala.quoted.{Expr, Quotes, Type}
import scala.compiletime.summonInline

object HasConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[HasConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    import util.{ParamRepr, TypeReprAsType}
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "HasConstructor")

    val typeRepr = TypeRepr.of[R].dealias.simplified

    if (typeRepr.typeSymbol == defn.AnyClass) {
      '{ HasConstructor.empty }.asExprOf[HasConstructor[R]]
    } else {
      val deepIntersection = ReflectionUtil
        .intersectionMembers(typeRepr)
        .map(_.dealias.simplified)
        .filter(_.typeSymbol != defn.AnyClass)

      val lamParams = deepIntersection.zipWithIndex.map {
        case (tpe, idx) =>
          val name = s"${tpe.typeSymbol.fullName}_$idx"
          ParamRepr(name, None, tpe)
      }

      val lamExpr = util.wrapIntoFunctoidRawLambda[ZEnvironment[R]](lamParams) {
        case (lambdaSym, allParams) =>
          (allParams.zip(lamParams) match {
            case (headParam, ParamRepr(_, _, TypeReprAsType('[t]))) :: params =>
              def addAccum[A <: zio.ZEnvironment[?], B](exprAcc: Typed, arg: Term)(using Type[B]): Typed = {
                val Typed(term, exprTpe) = exprAcc
                given Type[A] = exprTpe.tpe.asType.asInstanceOf[Type[A]]

                val addExpr = '{ ${ term.asExprOf[A] }.add[B](${ arg.asExprOf[B] })(summonInline[zio.Tag[B]]) }
                Typed(addExpr.asTerm, TypeTree.of[A & zio.ZEnvironment[B]])
              }

              params
                .foldLeft(
                  Typed('{ zio.ZEnvironment.apply[t](${ headParam.asExprOf[t] })(summonInline[zio.Tag[t]]) }.asTerm, TypeTree.of[zio.ZEnvironment[t]])
                ) {
                  case (expr, (arg, ParamRepr(_, _, tpe))) =>
                    tpe.asType match {
                      case '[b] =>
                        addAccum[zio.ZEnvironment[?], b](expr, arg)
                    }
                }
            case _ =>
              report.errorAndAbort(s"Impossible happened, empty Has intersection or malformed type ${typeRepr.show} in HasConstructorMacro")
          }).changeOwner(lambdaSym)
      }

      val f = util.makeFunctoid[ZEnvironment[R]](lamParams, lamExpr, '{ ProviderType.ZIOEnvironment })
      '{ new HasConstructor[R](${ f }) }
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
