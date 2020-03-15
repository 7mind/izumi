package izumi.distage.constructors.macros

import com.github.ghik.silencer.silent
import izumi.distage.constructors.{AnyConstructor, AnyConstructorOptionalMakeDSL, DebugProperties}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

@silent("deprecated.*since 2.11")
object AnyConstructorMacro {

  def make[B[_], T: c.WeakTypeTag](c: blackbox.Context): c.Expr[B[T]] = {
    import c.universe._
    c.Expr[B[T]](q"""${c.prefix}._make[${weakTypeOf[T]}]((${c.inferImplicitValue(weakTypeOf[AnyConstructorOptionalMakeDSL[T]], silent = false)}))""")
  }

  def anyConstructorOptionalMakeDSL[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructorOptionalMakeDSL[T]] = {
    import c.universe._

    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val enclosingClass = c.enclosingClass
    // We expect this macro to be called only and __ONLY__ from `AnyConstructorMacro.make`
    // we're going to use the position of the `make` call to search for subsequent methods
    // instead of the position of the implicit search itself (which is unstable and
    // sometimes doesn't exist, for example during scaladoc compilation)
    val positionOfMakeCall: Universe#Position = c.enclosingMacros(1).macroApplication.pos

    assert(enclosingClass.exists(_.pos == positionOfMakeCall), "enclosingClass must contain macro call position")

    def findExprContainingMake(tree: Tree): Option[Tree] = {
      val afterLastBlock = Option {
        tree
          .filter(_.exists(_.pos == positionOfMakeCall))
          .reverseIterator
          .takeWhile { case _: BlockApi | _: TemplateApi | _: DefTreeApi => false; case _ => true }
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

            mkAnyConstructor[T](c).tree
          } else {
            logger.log(s"For $tpe found `.from`-like calls, generating ERROR constructor: $nonwhiteListedMethods")

            q"""_root_.izumi.distage.constructors.AnyConstructorOptionalMakeDSL.errorConstructor[$tpe](${tpe.toString}, $nonwhiteListedMethods)"""
          }
      }
    }
  }

  def mkAnyConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AnyConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("AnyConstructor", targetType)

    if (reflectionProvider.isConcrete(targetType)) {
      ClassConstructorMacro.mkClassConstructor[T](c)
    } else if (reflectionProvider.isFactory(targetType)) {
      FactoryConstructorMacro.mkFactoryConstructor[T](c)
    } else if (reflectionProvider.isWireableAbstract(targetType)) {
      TraitConstructorMacro.mkTraitConstructor[T](c)
    } else {
      c.abort(
        c.enclosingPosition,
        s"""AnyConstructor failure: couldn't generate a constructor for $targetType!
           |It's neither a concrete class, nor a factory, nor a trait!""".stripMargin
      )
    }
  }

}
