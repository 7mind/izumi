package izumi.distage.testkit.scalatest

import cats.effect.IO
import izumi.distage.testkit.scalatest.AssertCIO.AssertCIOMacro
import izumi.fundamentals.reflection.ReflectionUtil
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[cats.effect.IO]] */
trait AssertCIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): IO[Assertion] = macro AssertCIOMacro.impl

  final def assertIO[T](
    effect: IO[T]
  )(predicate: T => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
  ): IO[Assertion] = macro AssertCIOMacro.shortImpl1[T]

  final def assertIO[A, B](
    effectA: IO[A],
    effectB: IO[B],
  )(predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
  ): IO[Assertion] = macro AssertCIOMacro.shortImpl2[A, B]
}

object AssertCIO extends AssertCIO {

  object AssertCIOMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[IO[Assertion]] = {
      import c.universe._
      c.Expr[IO[Assertion]](q"_root_.cats.effect.IO.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }

    def shortImpl1[T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[IO[T]]
    )(predicate: c.Expr[T => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[IO[Assertion]] = {
      import c.universe._

      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultName.toString))

      c.Expr[IO[Assertion]](
        q"$effect.flatMap { ($resultName: ${weakTypeOf[T]}) => _root_.izumi.distage.testkit.scalatest.AssertCIO.assertIO($predicateBody)($prettifier, $pos) }"
      )
    }

    def shortImpl2[A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[IO[A]],
      effectB: c.Expr[IO[B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
    ): c.Expr[IO[Assertion]] = {
      import c.universe._

      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultAName.toString, resultBName.toString))

      c.Expr[IO[Assertion]](q"""$effectA.flatMap {
           ($resultAName: ${weakTypeOf[A]}) =>
              $effectB.flatMap { ($resultBName: ${weakTypeOf[B]}) => _root_.izumi.distage.testkit.scalatest.AssertCIO.assertIO($predicateBody)($prettifier, $pos) }
        }""")
    }
  }

}
