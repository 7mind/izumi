package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertIO3.AssertIO3Macro
import izumi.functional.bio.IO3
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for any [[izumi.functional.bio.IO3]] */
trait AssertIO3[F[-_, +_, +_]] {
  final def assertIO(arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] = macro AssertIO3Macro.impl[F]
}

object AssertIO3 {
  final def assertIO[F[-_, +_, +_]](arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] = macro
    AssertIO3Macro.impl[F]

  object AssertIO3Macro {
    def impl[F[-_, +_, +_]](
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(IO3: c.Expr[IO3[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[org.scalactic.source.Position],
    ): c.Expr[F[Any, Nothing, Assertion]] = {
      import c.universe._
      c.Expr[F[Any, Nothing, Assertion]](q"$IO3.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }
}
