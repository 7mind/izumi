package izumi.distage.testkit.distagesuite.generic

import cats.effect.IO as CIO
import izumi.fundamentals.platform.functional.Identity
import zio.{Task, ZEnv, ZIO}

final class DistageTestExampleId extends DistageTestExampleBase[Identity]
final class DistageTestExampleCIO extends DistageTestExampleBase[CIO]
final class DistageTestExampleZIO extends DistageTestExampleBase[Task]
final class DistageTestExampleZIOZEnv extends DistageTestExampleBase[ZIO[ZEnv, Throwable, +_]]

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

final class OverloadingTestCIO extends OverloadingTest[CIO]
final class OverloadingTestTask extends OverloadingTest[Task]
final class OverloadingTestIdentity extends OverloadingTest[Identity]

final class ActivationTestCIO extends ActivationTest[CIO]
final class ActivationTestTask extends ActivationTest[Task]
final class ActivationTestIdentity extends ActivationTest[Identity]

final class ForcedRootTestCIO extends ForcedRootTest[CIO]
final class ForcedRootTestTask extends ForcedRootTest[Task]
final class ForcedRootTestIdentity extends ForcedRootTest[Identity]
