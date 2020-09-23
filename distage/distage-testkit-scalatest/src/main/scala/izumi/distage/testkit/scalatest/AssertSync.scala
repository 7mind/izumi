package izumi.distage.testkit.scalatest

import cats.effect.Sync
import izumi.distage.testkit.scalatest.AssertSync.AssertSyncMacro
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait AssertSync {
  final def assertIO[F[_]](arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = macro AssertSyncMacro.impl[F]
}

object AssertSync extends AssertSync {

  object AssertSyncMacro {
    def impl[F[_]](
      c: blackbox.Context
    )(arg: c.Expr[Boolean]
    )(Sync: c.Expr[Sync[F]],
      prettifier: c.Expr[Prettifier],
      pos: c.Expr[org.scalactic.source.Position],
    ): c.Expr[F[Assertion]] = {
      import c.universe._
      c.Expr[F[Assertion]](q"$Sync.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
