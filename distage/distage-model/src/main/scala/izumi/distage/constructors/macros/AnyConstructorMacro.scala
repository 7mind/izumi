package izumi.distage.constructors.macros

import izumi.distage.constructors.{AnyConstructor, AnyConstructorOptionalMakeDSL, DebugProperties}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, false)
  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, true)

  def optional[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructorOptionalMakeDSL[T]] = {
    import c.universe._

    def findBlockWithMakeExpr(t: Tree): Option[Tree] = {
//      val maybeTrees = t.collect {
//        case Template(_, _, l) => l.find(_.exists(_.pos == c.macroApplication.pos))
//        case Block(init, last) =>
//          (init.iterator ++ Iterator.single(last))
//            .find(_.exists(_.pos == c.macroApplication.pos))
//      }
//      val afterLastBlock = maybeTrees.flatMap(_.toList).lastOption

      val afterLastBlock = Option {
        t.filter(_.exists(_.pos == c.macroApplication.pos))
          .reverseIterator
          .takeWhile(!_.isInstanceOf[BlockApi])
          .foldLeft(null: Tree)((_, t) => t) // .last for iterator
      }

      afterLastBlock
    }

    val maybeNonwhiteListedMethods = findBlockWithMakeExpr(c.enclosingClass).map(_.collect {
      case Select(lhs, TermName(method))
        if !ModuleDefDSL.MakeDSLNoOpMethodsWhitelist.contains(method) &&
          lhs.exists(_.pos == c.macroApplication.pos) =>
        method
    })

    val tpe = weakTypeOf[T]
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    c.Expr[AnyConstructorOptionalMakeDSL[T]] {
      maybeNonwhiteListedMethods match {
        case None =>
          logger.log(s"Scaladoc shenanigans: got `EmptyTree` for macroApplication. Directly generating an error:")

          q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.throwError(${tpe.toString}, Nil, true)"""
        case Some(nonwhiteListedMethods) =>
          if (nonwhiteListedMethods.isEmpty) {
            logger.log(
              s"""For $tpe found no `.from`-like calls in ${findBlockWithMakeExpr(c.enclosingClass)} (raw tree: ${showRaw(findBlockWithMakeExpr(c.enclosingClass))})
                  |enclosingClass contains macro call (must be true): ${
                val res = c.enclosingClass.exists(_.pos == c.macroApplication.pos)
                assert(res, "enclosingClass must contain macro call position")
                res}
                  |enclosingUnit contains macro call (can be non-true for inline typechecks): ${c.enclosingUnit.body.exists(_.pos == c.macroApplication.pos)}
                  |""".stripMargin)

            q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.apply[$tpe](${mkAnyConstructorImpl[T](c, false)})"""
          } else {
            logger.log(s"For $tpe found `.from`-like calls, generating ERROR constructor: $nonwhiteListedMethods")

            q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.errorConstructor[$tpe](${tpe.toString}, $nonwhiteListedMethods)"""
          }
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
