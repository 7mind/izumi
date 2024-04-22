package izumi.distage.constructors

import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.FunctoidMacro

import izumi.distage.model.reflection.Provider.ProviderType

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.ArraySeq

object ClassConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = try {
    import qctx.reflect.*

    val util = new ConstructorUtil[qctx.type]()
    util.requireConcreteTypeConstructor(TypeRepr.of[R], "ClassConstructor")

    val typeRepr = TypeRepr.of[R].dealias.simplified
    val typeSymbol = typeRepr.typeSymbol
    val tpeDeref = util.dereferenceTypeRef(typeRepr)

    // FIXME remove redundant check across macros
    lazy val context = new ConstructorContext[R, qctx.type, util.type](util)

    val isConcrete =
      (typeRepr.classSymbol.isDefined && !util.symbolIsTraitOrAbstract(typeSymbol) && !isRefinement(tpeDeref))
      || isSingleton(tpeDeref)

    if (isConcrete) {
      makeImpl[R](util, typeRepr, tpeDeref)
    } else if (context.isWireableTrait) {
      report.errorAndAbort(
        s"ClassConstructor failure: ${Type.show[R]} is a trait or an abstract class, use `makeTrait` or `make[X].fromTrait` to wire traits."
      )
    } else if (context.isFactoryOrTrait) {
      report.errorAndAbort(
        s"ClassConstructor failure: ${Type.show[R]} is a Factory, use `makeFactory` or `make[X].fromFactory` to wire factories."
      )
    } else {
      report.errorAndAbort(
        s"""ClassConstructor failure: couldn't derive a constructor for ${Type.show[R]}!
           |It's neither a concrete class, nor a wireable trait or abstract class!""".stripMargin
      )
    }
  } catch { case t: scala.quoted.runtime.StopMacroExpansion => throw t; case t: Throwable => qctx.reflect.report.errorAndAbort(t.stacktraceString) }

  def makeImpl[R: Type](
    using qctx: Quotes
  )(util: ConstructorUtil[qctx.type],
    typeRepr: qctx.reflect.TypeRepr,
    tpeDeref: qctx.reflect.TypeRepr,
  ): Expr[ClassConstructor[R]] = {
    import qctx.reflect.*

    tpeDeref match {
      case c: ConstantType =>
        singletonClassConstructor[R](Literal(c.constant))

      case t: TermRef =>
        singletonClassConstructor[R](Ident(t))

      case _ =>
        typeRepr.classSymbol match {
          case Some(_) =>
            val ctorTreeParameterized = util.buildConstructorTermAppliedToTypeParameters(typeRepr)
            val paramss = util.extractConstructorParamLists(typeRepr)
            val lamExpr = util.wrapCtorApplicationIntoFunctoidRawLambda[R](paramss, ctorTreeParameterized)

            val f = util.makeFunctoid[R](paramss.flatten, lamExpr, '{ ProviderType.Class })
            '{ new ClassConstructor[R](${ f }) }

          case None =>
            report.errorAndAbort(s"No class symbol defined for $typeRepr")
        }
    }
  }

  private def isSingleton(using qctx: Quotes)(tpeDeref: qctx.reflect.TypeRepr): Boolean = {
    tpeDeref match {
      case _: qctx.reflect.ConstantType | _: qctx.reflect.TermRef => true
      case _ => false
    }
  }

  private def isRefinement(using qctx: Quotes)(tpeDeref: qctx.reflect.TypeRepr): Boolean = {
    tpeDeref match {
      case _: qctx.reflect.Refinement => true
      case _ => false
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
