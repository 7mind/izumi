package izumi.distage.modules.platform

import cats.effect.unsafe.IORuntime
import izumi.distage.model.definition.{Lifecycle, ModuleDef}

import scala.concurrent.ExecutionContext

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createDefaultBlockingExecutionContext()
      )(release = _._2.apply()).map(_._1)
  }
}
