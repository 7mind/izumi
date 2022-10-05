package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
import scala.collection.immutable.ArraySeq

object ClassConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()
    val util = new ConstructorUtil[qctx.type]()
    import util.ParamListExt

    Expr.summon[ValueOf[R]] match {
      case Some(valexpr) =>
        '{ new ClassConstructor[R](Functoid.singleton(${ valexpr.asExprOf[scala.Singleton & R] })) }
      case _ =>
        util.requireConcreteTypeConstructor[R]("ClassConstructor")

        val typeRepr = TypeRepr.of[R].dealias.simplified

        typeRepr.classSymbol match {
          case Some(cs) =>
            val ctorTreeParameterized = util.buildConstructorApplication(cs, typeRepr)
            val constructorParamLists = List(util.buildConstructorParameters(typeRepr)(cs))
            val flatCtorParams = constructorParamLists.flatMap(_._2.iterator.flatten)

            val lamExpr = util.wrapApplicationIntoLambda[R](List(flatCtorParams.toTrees), ctorTreeParameterized)
            val f = functoidMacro.make[R](lamExpr)
            '{ new ClassConstructor[R](${ f }) }
          case None =>
            report.errorAndAbort(s"No class symbol defined for $typeRepr")
        }
    }
  } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
