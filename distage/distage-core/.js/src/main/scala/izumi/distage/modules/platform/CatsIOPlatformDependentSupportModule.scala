package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef

import scala.concurrent.ExecutionContext

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").using[ExecutionContext]("cpu")
}
