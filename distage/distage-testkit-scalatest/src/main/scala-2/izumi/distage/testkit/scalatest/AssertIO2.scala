package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertIO2.AssertIO2Macro
import izumi.functional.bio.IO2
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] {
  final def assertIO(arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]
}

object AssertIO2 {
  final def assertIO[F[+_, +_]](arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]

  object AssertIO2Macro {
    def impl[F[+_, +_]](
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(IO2: c.Expr[IO2[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[org.scalactic.source.Position],
    ): c.Expr[F[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[F[Nothing, Assertion]](q"$IO2.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }
}
