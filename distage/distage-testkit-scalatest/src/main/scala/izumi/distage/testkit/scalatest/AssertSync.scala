package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

/** scalatest assertion macro for any [[cats.effect.kernel.Sync]] */
trait AssertSync[F[_]] extends AssertSyncImpl[F] {}

object AssertSync extends AssertSyncStaticImpl {}
