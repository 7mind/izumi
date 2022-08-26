package izumi.distage.testkit.scalatest

/** scalatest assertion macro for any [[cats.effect.kernel.Sync]] */
trait AssertSync[F[_]] extends AssertSyncImpl[F] {}

object AssertSync extends AssertSyncStaticImpl {}
