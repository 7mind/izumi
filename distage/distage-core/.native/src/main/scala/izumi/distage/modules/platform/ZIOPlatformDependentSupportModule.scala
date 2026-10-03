package izumi.distage.modules.platform

import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import zio.Executor

import java.util.concurrent.Executors

private[modules] abstract class ZIOPlatformDependentSupportModule[R] extends ModuleDef {
  make[Executor].named("cpu").fromResource {
    Lifecycle.fromExecutorService(Executors.newWorkStealingPool()).map(Executor.fromJavaExecutor)
  }
}
