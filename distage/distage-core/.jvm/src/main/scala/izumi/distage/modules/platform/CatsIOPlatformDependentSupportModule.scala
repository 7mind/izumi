package izumi.distage.modules.platform

import cats.effect.Blocker
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .fromExecutorService(Executors.newCachedThreadPool())
      .map(ExecutionContext.fromExecutor)
  }
  make[Blocker].from(Blocker.liftExecutionContext(_: ExecutionContext @Id("io")))
}
