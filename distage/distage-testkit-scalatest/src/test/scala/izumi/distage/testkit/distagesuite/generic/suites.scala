package izumi.distage.testkit.distagesuite.generic

import cats.effect.IO as CIO
import zio.{Task, ZIO}

final class DistageTestExampleCIO extends DistageTestExampleBase[CIO]
final class DistageTestExampleZIO extends DistageTestExampleBase[Task]
final class DistageTestExampleZIOZEnv extends DistageTestExampleBase[ZIO[Int, Throwable, +_]]

final class OverloadingTestCIO extends OverloadingTest[CIO]
final class OverloadingTestTask extends OverloadingTest[Task]

final class ActivationTestCIO extends ActivationTest[CIO]
final class ActivationTestTask extends ActivationTest[Task]

final class ForcedRootTestCIO extends ForcedRootTest[CIO]
final class ForcedRootTestTask extends ForcedRootTest[Task]
