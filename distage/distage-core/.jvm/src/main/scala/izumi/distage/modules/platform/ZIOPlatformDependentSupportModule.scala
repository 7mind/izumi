package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import zio.Executor

private[modules] abstract class ZIOPlatformDependentSupportModule[R] extends ModuleDef {
  make[Executor].named("cpu").from {
    //    Runtime.defaultExecutor
    Executor.makeDefault() // Even though ZIO executor seems to be optimized for globality, we'd rather still make isolated pools, for safety.
    // It doesn't seem to have a closing action for some reason, so not using fromResource here.
  }
}
