package izumi.distage.constructors.macros

import izumi.distage.constructors.{AnyConstructor, AnyConstructorOptionalMakeDSL, DebugProperties}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object AnyConstructorMacro {
  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, false)
  def mkAnyConstructorUnsafeWeakSafeTypes[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = mkAnyConstructorImpl[T](c, true)

  def make[B[_], T: c.WeakTypeTag](c: blackbox.Context): c.Expr[B[T]] = {
    import c.universe._

    c.Expr[B[T]](q"""${c.prefix}._make[${weakTypeOf[T]}]((${c.inferImplicitValue(weakTypeOf[AnyConstructorOptionalMakeDSL[T]], silent = false)}).provider)""")
  }

  def anyConstructorOptionalMakeDSL[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructorOptionalMakeDSL[T]] = {
    import c.universe._

    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val enclosingClass = c.enclosingClass
    val positionOfMakeCall = c.enclosingMacros(1).macroApplication.pos

    assert(enclosingClass.exists(_.pos == positionOfMakeCall), "enclosingClass must contain macro call position")

    def findExprContainingMake(tree: Tree): Option[Tree] = {
      val afterLastBlock = Option {
        tree
          .filter(_.exists(_.pos == positionOfMakeCall))
          .reverseIterator
          .takeWhile { case _: BlockApi | _ : TemplateApi | _ : DefTreeApi => false ; case _ => true }
          .foldLeft(null: Tree)((_, t) => t) // .last for iterator
      }
      afterLastBlock
    }

    val maybeTree = findExprContainingMake(enclosingClass)

    val maybeNonwhiteListedMethods = maybeTree.map(_.collect {
      case Select(lhs, TermName(method))
        if !ModuleDefDSL.MakeDSLNoOpMethodsWhitelist.contains(method) &&
          lhs.exists(_.pos == positionOfMakeCall) =>
        method
    })

    logger.log(
      s"""Got tree: $maybeTree
         |Result of search: $maybeNonwhiteListedMethods
         |Searched for position: $positionOfMakeCall
         |Positions: ${c.macroApplication.pos -> c.enclosingMacros.map(_.macroApplication.pos)}
         |enclosingUnit contains macro call (can be non-true for inline typechecks): ${c.enclosingUnit.body.exists(_.pos == positionOfMakeCall)}
         |""".stripMargin)


    val tpe = weakTypeOf[T]

    c.Expr[AnyConstructorOptionalMakeDSL[T]] {
      maybeNonwhiteListedMethods match {
        case None =>
          c.abort(c.enclosingPosition,
            s"""Couldn't find position of the `make` call when summoning AnyConstructorOptionalMakeDSL[$tpe]
               |Got tree: $maybeTree
               |Result of search: $maybeNonwhiteListedMethods
               |Searched for position: $positionOfMakeCall
               |Positions: ${c.macroApplication.pos -> c.enclosingMacros.map(_.macroApplication.pos)}
               |enclosingUnit contains macro call (can be non-true for inline typechecks): ${c.enclosingUnit.body.exists(_.pos == positionOfMakeCall)}
               |""".stripMargin)
        case Some(nonwhiteListedMethods) =>
          if (nonwhiteListedMethods.isEmpty) {
            logger.log(s"""For $tpe found no `.from`-like calls in $maybeTree""".stripMargin)

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
