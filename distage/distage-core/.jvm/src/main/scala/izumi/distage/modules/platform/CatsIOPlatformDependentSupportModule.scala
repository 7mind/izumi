package izumi.distage.modules.platform

import izumi.distage.model.definition.{Lifecycle, ModuleDef}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .fromExecutorService(Executors.newCachedThreadPool())
      .map(ExecutionContext.fromExecutor)
  }
  // FIXME cats Blocker
//  make[Blocker].from(Blocker.liftExecutionContext(_: ExecutionContext @Id("io")))
}
