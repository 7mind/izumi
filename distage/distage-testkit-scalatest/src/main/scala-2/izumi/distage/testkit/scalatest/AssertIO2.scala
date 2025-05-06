package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertIO2.AssertIO2Macro
import izumi.functional.bio.IO2
import izumi.fundamentals.reflection.ReflectionUtil
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] {
  final def assertIO(arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]
  final def assertIO[T](
    effect: F[Nothing, T]
  )(predicate: T => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[Nothing, Assertion] = macro AssertIO2Macro.shortImpl1[F, T]

  final def assertIO[A, B](
    effectA: F[Nothing, A],
    effectB: F[Nothing, B],
  )(predicate: (A, B) => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[Nothing, Assertion] = macro AssertIO2Macro.shortImpl2[F, A, B]
}

object AssertIO2 {
  final def assertIO[F[+_, +_]](arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]

  object AssertIO2Macro {
    def impl[F[+_, +_]](
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[F[Nothing, Assertion]](q"$IO2.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }

    def shortImpl1[F[+_, +_], T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[F[Nothing, T]]
    )(predicate: c.Expr[T => Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Nothing, Assertion]] = {
      import c.universe._

      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda1[T, Boolean](c)(predicate, resultName.toString)

      c.Expr[F[Nothing, Assertion]](q"$IO2.flatMap($effect) { ($resultName: ${weakTypeOf[T]}) => assertIO($predicateBody)($IO2, $prettifier, $pos) }")
    }

    def shortImpl2[F[+_, +_], A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[F[Nothing, A]],
      effectB: c.Expr[F[Nothing, B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[Nothing, Assertion]] = {
      import c.universe._

      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda2[A, B, Boolean](c)(predicate, resultAName.toString, resultBName.toString)

      c.Expr[F[Nothing, Assertion]](q"""$IO2.flatMap($effectA) {
           ($resultAName: ${weakTypeOf[A]}) =>
              $IO2.flatMap($effectB) { ($resultBName: ${weakTypeOf[B]}) => assertIO($predicateBody)($IO2, $prettifier, $pos) }
        }""")
    }
  }
}
