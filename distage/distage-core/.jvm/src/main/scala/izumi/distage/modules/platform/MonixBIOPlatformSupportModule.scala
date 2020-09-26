package izumi.distage.modules.platform

import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.functional.bio.{BIORunner, BlockingIO, BlockingIOInstances}
import monix.bio.IO
import monix.execution.Scheduler

object MonixBIOPlatformSupportModule extends ModuleDef {
  make[BIORunner[IO]].from[BIORunner.MonixBIORunner]
  make[BlockingIO[IO]].from(BlockingIOInstances.BlockingMonixBIOFromScheduler(_: Scheduler @Id("io")))
}
