package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.distage.model.reflection.Provider.ProviderType

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.collection.immutable.ArraySeq

object ClassConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "ClassConstructor")

    makeImpl[R](util)
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  def makeImpl[R: Type](using qctx: Quotes)(util: ConstructorUtil[qctx.type]): Expr[ClassConstructor[R]] = {
    import qctx.reflect.*

    val typeRepr = TypeRepr.of[R].dealias.simplified
    util.dereferenceTypeRef(typeRepr) match {
      case c: ConstantType =>
        singletonClassConstructor[R](Literal(c.constant))

      case t: TermRef =>
        singletonClassConstructor[R](Ident(t))

      case _ =>
        if (typeRepr.typeSymbol.flags.is(Flags.Trait) || typeRepr.typeSymbol.flags.is(Flags.Abstract)) {
          report.errorAndAbort(
            s"Cannot create ClassConstructor for type ${Type.show[R]} - it's a trait or an abstract class, not a concrete class. It cannot be constructed with `new`"
          )
        }

        typeRepr.classSymbol match {
          case Some(_) =>
            val ctorTreeParameterized = util.buildConstructorApplication(typeRepr)
            val paramss = util.buildConstructorParameters(typeRepr)
            val lamExpr = util.wrapCtorApplicationIntoFunctoidRawLambda[R](paramss, ctorTreeParameterized)

            val f = util.makeFunctoid[R](paramss.flatten, lamExpr, '{ ProviderType.Class })
            '{ new ClassConstructor[R](${ f }) }

          case None =>
            report.errorAndAbort(s"No class symbol defined for $typeRepr")
        }
    }
  }

  private def singletonClassConstructor[R0](using qctx: Quotes, rtpe0: Type[R0])(tree: qctx.reflect.Tree): Expr[ClassConstructor[R0]] = {
    type R <: R0 & Singleton
    (rtpe0: @unchecked) match {
      case given Type[R] =>
        '{ new ClassConstructor[R](Functoid.singleton[R](${ tree.asExprOf[R] })) }
    }
  }

}
