package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for any [[cats.effect.kernel.Sync]] */
trait AssertSync[F[_]] {
  inline final def assertIO(inline arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.delay(Assertions.assert(arg))
  }
}

object AssertSync {
  inline final def assertIO[F[_]](inline arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.delay(Assertions.assert(arg))
  }
}
