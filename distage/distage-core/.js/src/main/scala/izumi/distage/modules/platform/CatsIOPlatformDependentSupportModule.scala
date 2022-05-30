package izumi.distage.modules.platform

import cats.effect.unsafe.IORuntime
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.fundamentals.platform.functional.Identity

import scala.annotation.unused
import scala.concurrent.ExecutionContext

private[distage] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").using[ExecutionContext]("cpu")

  protected[this] def createCPUPool(@unused ioRuntime: => IORuntime): Lifecycle[Identity, ExecutionContext] = {
    Lifecycle.pure(IORuntime.defaultComputeExecutionContext)
  }
}
