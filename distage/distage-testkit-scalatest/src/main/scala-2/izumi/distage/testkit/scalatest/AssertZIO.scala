package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertZIO.AssertZIOMacro
import izumi.fundamentals.reflection.ReflectionUtil
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro
import zio.IO

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position, zioTrace: zio.Trace): IO[Nothing, Assertion] = macro AssertZIOMacro.impl

  final def assertIO[T](
    effect: IO[Nothing, T]
  )(predicate: T => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): IO[Nothing, Assertion] = macro AssertZIOMacro.shortImpl1[T]

  final def assertIO[A, B](
    effectA: IO[Nothing, A],
    effectB: IO[Nothing, B],
  )(predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): IO[Nothing, Assertion] = macro AssertZIOMacro.shortImpl2[A, B]
}

object AssertZIO extends AssertZIO {

  object AssertZIOMacro {
    def impl(
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
      zioTrace: c.Expr[zio.Trace],
    ): c.Expr[IO[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[IO[Nothing, Assertion]](q"_root_.zio.ZIO.succeed(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})($zioTrace)")
    }

    def shortImpl1[T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[IO[Nothing, T]]
    )(predicate: c.Expr[T => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
      zioTrace: c.Expr[zio.Trace],
    ): c.Expr[IO[Nothing, Assertion]] = {
      import c.universe._

      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda1[T, Boolean](c)(predicate, resultName.toString)

      c.Expr[IO[Nothing, Assertion]](q"$effect.flatMap { ($resultName: ${weakTypeOf[T]}) => assertIO($predicateBody)($prettifier, $pos, $zioTrace) }")
    }

    def shortImpl2[A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[IO[Nothing, A]],
      effectB: c.Expr[IO[Nothing, B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
      zioTrace: c.Expr[zio.Trace],
    ): c.Expr[IO[Nothing, Assertion]] = {
      import c.universe._

      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda2[A, B, Boolean](c)(predicate, resultAName.toString, resultBName.toString)

      c.Expr[IO[Nothing, Assertion]](q"""$effectA.flatMap {
           ($resultAName: ${weakTypeOf[A]}) =>
              $effectB.flatMap { ($resultBName: ${weakTypeOf[B]}) => assertIO($predicateBody)($prettifier, $pos, $zioTrace) }
        }""")
    }
  }

}
