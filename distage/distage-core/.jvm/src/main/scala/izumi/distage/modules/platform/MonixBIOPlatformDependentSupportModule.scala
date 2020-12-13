package izumi.distage.modules.platform

import izumi.distage.model.definition.Id
import izumi.functional.bio.{BlockingIO2, BlockingIOInstances, UnsafeRun2}
import monix.bio.IO
import monix.execution.Scheduler

private[modules] trait MonixBIOPlatformDependentSupportModule extends MonixPlatformDependentSupportModule {
  make[UnsafeRun2[IO]].from[UnsafeRun2.MonixBIORunner]
  make[BlockingIO2[IO]].from(BlockingIOInstances.BlockingMonixBIOFromScheduler(_: Scheduler @Id("io")))
}
