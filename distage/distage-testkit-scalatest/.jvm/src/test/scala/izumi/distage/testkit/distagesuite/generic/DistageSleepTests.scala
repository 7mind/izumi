package izumi.distage.testkit.distagesuite.generic

import cats.effect.IO as CIO
import distage.TagK
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.distagesuite.fixtures.MockUserRepository
import izumi.distage.testkit.distagesuite.generic.DistageTestExampleBase.DistageMemoizeExample
import izumi.distage.testkit.scalatest.Spec1
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.fundamentals.platform.functional.Identity
import zio.Task

// JVM-only tests that use Thread.sleep
abstract class DistageSleepTest[F[_]: TagK: DefaultModule](implicit F: QuasiIO[F]) extends Spec1[F] with DistageMemoizeExample[F] {
  "distage test" should {
    "sleep" in {
      (_: MockUserRepository[F]) =>
        for {
          _ <- F.maybeSuspend(Thread.sleep(100))
        } yield ()
    }
  }
}

final class DistageSleepTest01 extends DistageSleepTest[CIO]
final class DistageSleepTest02 extends DistageSleepTest[CIO]
final class DistageSleepTest03 extends DistageSleepTest[CIO]
final class DistageSleepTest04 extends DistageSleepTest[CIO]
final class DistageSleepTest05 extends DistageSleepTest[CIO]
final class DistageSleepTest06 extends DistageSleepTest[CIO]
final class DistageSleepTest07 extends DistageSleepTest[CIO]
final class DistageSleepTest08 extends DistageSleepTest[CIO]
final class DistageSleepTest09 extends DistageSleepTest[CIO]
final class IdentityDistageSleepTest01 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest02 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest03 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest04 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest05 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest06 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest07 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest08 extends DistageSleepTest[Identity]
final class IdentityDistageSleepTest09 extends DistageSleepTest[Identity]
final class TaskDistageSleepTest01 extends DistageSleepTest[Task]
final class TaskDistageSleepTest02 extends DistageSleepTest[Task]
final class TaskDistageSleepTest03 extends DistageSleepTest[Task]
final class TaskDistageSleepTest04 extends DistageSleepTest[Task]
final class TaskDistageSleepTest05 extends DistageSleepTest[Task]
final class TaskDistageSleepTest06 extends DistageSleepTest[Task]
final class TaskDistageSleepTest07 extends DistageSleepTest[Task]
final class TaskDistageSleepTest08 extends DistageSleepTest[Task]
final class TaskDistageSleepTest09 extends DistageSleepTest[Task]
