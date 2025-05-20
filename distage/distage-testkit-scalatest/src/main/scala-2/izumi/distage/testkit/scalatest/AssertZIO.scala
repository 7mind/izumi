package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertZIO.AssertZIOMacro
import izumi.fundamentals.reflection.ReflectionUtil
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro
import zio.{IO, ZIO}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position, zioTrace: zio.Trace): IO[Nothing, Assertion] = macro AssertZIOMacro.impl

  final def assertIO[R, E, T](
    effect: ZIO[R, E, T]
  )(predicate: T => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): ZIO[R, E, Assertion] = macro AssertZIOMacro.shortImpl1[R, E, T]

  final def assertIO[R, E, A, B](
    effectA: ZIO[R, E, A],
    effectB: ZIO[R, E, B],
  )(predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): ZIO[R, E, Assertion] = macro AssertZIOMacro.shortImpl2[R, E, A, B]
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

    def shortImpl1[R: c.WeakTypeTag, E: c.WeakTypeTag, T: c.WeakTypeTag](
      c: blackbox.Context
    )(effect: c.Expr[ZIO[R, E, T]]
    )(predicate: c.Expr[T => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
      zioTrace: c.Expr[zio.Trace],
    ): c.Expr[ZIO[R, E, Assertion]] = {
      import c.universe._

      val resultName = TermName(c.freshName("result"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultName.toString))

      c.Expr[IO[Nothing, Assertion]](
        q"$effect.flatMap { ($resultName: ${weakTypeOf[T]}) => _root_.izumi.distage.testkit.scalatest.AssertZIO.assertIO($predicateBody)($prettifier, $pos, $zioTrace) }"
      )
    }

    def shortImpl2[R: c.WeakTypeTag, E: c.WeakTypeTag, A: c.WeakTypeTag, B: c.WeakTypeTag](
      c: blackbox.Context
    )(effectA: c.Expr[ZIO[R, E, A]],
      effectB: c.Expr[ZIO[R, E, B]],
    )(predicate: c.Expr[(A, B) => Boolean]
    )(prettifier: c.Expr[Prettifier],
      pos: c.Expr[Position],
      zioTrace: c.Expr[zio.Trace],
    ): c.Expr[ZIO[R, E, Assertion]] = {
      import c.universe._

      val resultAName = TermName(c.freshName("resultA"))
      val resultBName = TermName(c.freshName("resultB"))
      val predicateBody = ReflectionUtil.betaReduceLambda(c)(predicate, List(resultAName.toString, resultBName.toString))

      c.Expr[IO[Nothing, Assertion]](q"""$effectA.flatMap {
           ($resultAName: ${weakTypeOf[A]}) =>
              $effectB.flatMap { ($resultBName: ${weakTypeOf[B]}) =>
                _root_.izumi.distage.testkit.scalatest.AssertZIO.assertIO($predicateBody)($prettifier, $pos, $zioTrace) }
        }""")
    }
  }

}
