package izumi.distage.reflection.macros

import izumi.distage.constructors.{ClassConstructorOptionalMakeDSL, DebugProperties}
import izumi.distage.model.definition.dsl.ModuleDefDSL
import izumi.fundamentals.reflection.TrivialMacroLogger

import scala.annotation.nowarn
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

@nowarn("msg=deprecated.*since 2.11")
object MakeMacro {

  def make[B[_], T: c.WeakTypeTag](c: blackbox.Context): c.Expr[B[T]] = {
    import c.universe.*
    c.Expr[B[T]](q"""${c.prefix}._make[${weakTypeOf[T]}](${c.inferImplicitValue(weakTypeOf[ClassConstructorOptionalMakeDSL[T]], silent = false)}.provider)""")
  }

  def classConstructorOptionalMakeDSL[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ClassConstructorOptionalMakeDSL.Impl[T]] = {
    import c.universe.*

    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`.name)

    val enclosingClass = c.enclosingClass
    // We expect this macro to be called only and __ONLY__ from `MakeMacro.make`
    // we're going to use the position of the `make` call to search for subsequent methods
    // instead of the position of the implicit search itself (which is unstable and
    // sometimes doesn't exist, for example during scaladoc compilation)
    val positionOfMakeCall: Universe#Position = c.enclosingMacros(1).macroApplication.pos

    assert(enclosingClass.exists(_.pos == positionOfMakeCall), "enclosingClass must contain macro call position")

    def findExprContainingMake(tree: Tree): Option[Tree] = {
      @nowarn("msg=outer reference")
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

    logger.log(s"""Got tree: $maybeTree
                  |Result of search: $maybeNonwhiteListedMethods
                  |Searched for position: $positionOfMakeCall
                  |Positions: ${c.macroApplication.pos -> c.enclosingMacros.map(_.macroApplication.pos)}
                  |enclosingUnit contains macro call (can be non-true for inline typechecks): ${c.enclosingUnit.body.exists(_.pos == positionOfMakeCall)}
                  |""".stripMargin)

    val tpe = weakTypeOf[T]

    c.Expr[ClassConstructorOptionalMakeDSL.Impl[T]] {
      maybeNonwhiteListedMethods match {
        case None =>
          c.abort(
            c.enclosingPosition,
            s"""Couldn't find position of the `make` call when summoning ClassConstructorOptionalMakeDSL[$tpe]
               |Got tree: $maybeTree
               |Result of search: $maybeNonwhiteListedMethods
               |Searched for position: $positionOfMakeCall
               |Positions: ${c.macroApplication.pos -> c.enclosingMacros.map(_.macroApplication.pos)}
               |enclosingUnit contains macro call (can be non-true for inline typechecks): ${c.enclosingUnit.body.exists(_.pos == positionOfMakeCall)}
               |""".stripMargin,
          )
        case Some(nonwhiteListedMethods) =>
          if (nonwhiteListedMethods.isEmpty) {
            logger.log(s"""For $tpe found no `.from`-like calls in $maybeTree""".stripMargin)

            q"""_root_.izumi.distage.constructors.ClassConstructorOptionalMakeDSL.apply[$tpe](${ClassConstructorMacro.mkClassConstructor[T](c)}.provider)"""
          } else {
            logger.log(s"For $tpe found `.from`-like calls, generating ERROR constructor: $nonwhiteListedMethods")

            q"""_root_.izumi.distage.constructors.ClassConstructorOptionalMakeDSL.errorConstructor[$tpe](${tpe.toString}, $nonwhiteListedMethods)"""
          }
      }
    }
  }

}
