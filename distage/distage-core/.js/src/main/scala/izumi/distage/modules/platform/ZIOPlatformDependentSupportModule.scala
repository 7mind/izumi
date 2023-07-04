package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import zio.{Executor, Runtime}

private[modules] abstract class ZIOPlatformDependentSupportModule[R] extends ModuleDef {
  make[Executor].named("cpu").from {
    Runtime.defaultExecutor
  }
}
