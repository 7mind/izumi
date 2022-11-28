package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import izumi.fundamentals.platform.reflection.ReflectionUtil
import izumi.reflect.Tag

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
      zioHasConstructorAssertion(typeRepr, deepIntersection)

      val lamParams = deepIntersection.zipWithIndex.map {
        case (AppliedType(_, tpe :: _), idx) =>
          val name = s"${tpe.typeSymbol.fullName}_$idx"
          ParamRepr(name, None, tpe)
      }

      val lamExpr = util.wrapIntoFunctoidRawLambda[R](lamParams) {
        case (_, allParams) =>
          allParams.zip(lamParams) match {
            case (headParam, ParamRepr(_, _, TypeReprAsType('[t]))) :: params =>
              params
                .foldLeft('{ zio.Has.apply[t](${ headParam.asExprOf[t] })(summonInline[Tag[t]]) }) {
                  case (expr, (arg, ParamRepr(_, _, tpe))) =>
                    tpe.asType match {
                      case '[g] =>
                        '{ $expr.add(${ arg.asExprOf[g] })(summonInline[Tag[g]]) }
                    }
                }.asTerm
            case _ =>
              report.errorAndAbort(s"Impossible happened, empty Has intersection or malformed type ${typeRepr.show} in HasConstructorMacro")
          }
      }

      val f = util.makeFunctoid[R](lamParams, lamExpr, '{ ProviderType.ZIOHas })
      '{ new HasConstructor[R](${ f }) }
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  def zioHasConstructorAssertion(using qctx: Quotes)(typeRepr: qctx.reflect.TypeRepr, deepIntersection: List[qctx.reflect.TypeRepr]): Unit = {
    val (good, bad) = deepIntersection.partition(tpe => tpe.typeSymbol.fullName == "zio.Has")
    if (bad.nonEmpty) {
      qctx.reflect.report.errorAndAbort(
        s"Cannot construct an implementation for ZIO Has type `${typeRepr.show}`: intersection contains type constructors that aren't `zio.Has` or `Any`: ${bad
            .map(_.show)} (${bad.map(_.typeSymbol)})"
      )
    }
    if (good.isEmpty) {
      qctx.reflect.report.errorAndAbort(
        s"Cannot construct an implementation for ZIO Has type `${typeRepr.show}`: the intersection type is empty, it contains no `zio.Has` or `Any` type constructors in it, type was ${typeRepr.show} (${typeRepr.typeSymbol}"
      )
    }
  }

}
