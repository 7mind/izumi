package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertBIO.AssertBIOMacro
import izumi.functional.bio.BIO
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait AssertBIO {
  final def assertBIO[F[+_, +_]](arg: Boolean)(implicit BIO: BIO[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = macro AssertBIOMacro.impl[F]
}

object AssertBIO extends AssertBIO {

  object AssertBIOMacro {
    def impl[F[+_, +_]](c: blackbox.Context)(arg: c.Expr[Boolean])(BIO: c.Expr[BIO[F]], prettifier: c.Expr[Prettifier], pos: c.Expr[org.scalactic.source.Position]): c.Expr[F[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[F[Nothing, Assertion]](
        q"$BIO.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
