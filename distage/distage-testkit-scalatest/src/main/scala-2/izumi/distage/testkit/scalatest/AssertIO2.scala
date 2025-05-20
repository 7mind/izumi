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
  final def assertIO[E, T](
    effect: F[E, T]
  )(predicate: T => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[E, Assertion] = macro AssertIO2Macro.shortImpl1[F, E, T]

  final def assertIO[E, A, B](
    effectA: F[E, A],
    effectB: F[E, B],
  )(predicate: (A, B) => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: Position,
  ): F[E, Assertion] = macro AssertIO2Macro.shortImpl2[F, E, A, B]
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

    def shortImpl1[F[+_, +_], E: c.WeakTypeTag, T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[F[E, T]]
    )(predicate: c.Expr[T => Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[E, Assertion]] = {
      import c.universe._

      val io2FreshName = TermName(c.freshName("IO2"))
      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultName.toString))

      c.Expr[F[Nothing, Assertion]](
        q"""{
           val $io2FreshName = $IO2
           $io2FreshName.flatMap($effect) {
             ($resultName: ${weakTypeOf[T]}) => _root_.izumi.distage.testkit.scalatest.AssertIO2.assertIO($predicateBody)($io2FreshName, $prettifier, $pos)
           }}"""
      )
    }

    def shortImpl2[F[+_, +_], E: c.WeakTypeTag, A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[F[E, A]],
      effectB: c.Expr[F[E, B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[F[E, Assertion]] = {
      import c.universe._

      val io2FreshName = TermName(c.freshName("IO2"))
      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultAName.toString, resultBName.toString))

      c.Expr[F[Nothing, Assertion]](q"""{
        val $io2FreshName = $IO2
        $io2FreshName.flatMap($effectA) {
           ($resultAName: ${weakTypeOf[A]}) =>
              $io2FreshName.flatMap($effectB) { ($resultBName: ${weakTypeOf[B]}) =>
                _root_.izumi.distage.testkit.scalatest.AssertIO2.assertIO($predicateBody)($io2FreshName, $prettifier, $pos) }
        }}""")
    }
  }
}
