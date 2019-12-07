package izumi.distage.constructors.macros

import izumi.distage.constructors.{AnyConstructor, AnyConstructorOptionalMakeDSL}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, false)
  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, true)

  def optional[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructorOptionalMakeDSL[T]] = {
    import c.universe._

    def findBlockWithMakeExpr(t: Tree): c.universe.Tree = {
      val maybeTrees = t.collect {
        case Template(_, _, l) => l.find(_.exists(_.pos == c.macroApplication.pos))
        case Block(init, last) =>
          (init.iterator ++ Iterator.single(last))
            .find(_.exists(_.pos == c.macroApplication.pos))
      }
      maybeTrees.flatMap(_.toList).lastOption.getOrElse(EmptyTree)
    }

    val nonwhiteListedMethods = findBlockWithMakeExpr(c.enclosingClass).collect {
      case Select(lhs, TermName(method))
        if !ModuleDefDSL.MakeDSLNoOpMethodsWhitelist(method) && lhs.exists(_.pos == c.macroApplication.pos) =>
        method
    }

    val tpe = weakTypeOf[T]
    c.Expr[AnyConstructorOptionalMakeDSL[T]] {
      if (nonwhiteListedMethods.isEmpty) {
        q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.apply[$tpe](${mkAnyConstructorImpl[T](c, false)})"""
      } else {
        q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.error[$tpe](${tpe.toString},${ModuleDefDSL.MakeDSLNoOpMethodsWhitelist})"""
      }
    }
  }

  def mkAnyConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

    val tpe = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    if (reflectionProvider.isConcrete(tpe)) {
      ConcreteConstructorMacro.mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
    } else if (reflectionProvider.isFactory(tpe)) {
      FactoryConstructorMacro.mkFactoryConstructor[T](c)
    } else if (reflectionProvider.isWireableAbstract(tpe)) {
      TraitConstructorMacro.mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes)
    } else {
      c.abort(
        c.enclosingPosition,
        s"""AnyConstructor failure: couldn't generate a constructor for $tpe!
           |It's neither a concrete class, nor a factory, nor a trait!""".stripMargin
      )
    }
  }
}
