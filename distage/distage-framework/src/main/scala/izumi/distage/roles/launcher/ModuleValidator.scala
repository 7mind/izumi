package izumi.distage.roles.launcher

import distage.ModuleBase
import izumi.distage.roles.launcher.ModuleValidator.{ModulePair, ValidatedModulePair}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException

trait ModuleValidator {
  def validate(modulePair: ModulePair): ValidatedModulePair
}

object ModuleValidator {
  case class ModulePair(
    bootstrapAutoModule: ModuleBase,
    appModule: ModuleBase,
  )

  case class ValidatedModulePair(
    bootstrapAutoModule: ModuleBase,
    appModule: ModuleBase,
  )

  class ModuleValidatorImpl extends ModuleValidator {
    override def validate(modulePair: ModulePair): ValidatedModulePair = {
      val conflicts = modulePair.bootstrapAutoModule.keys.intersect(modulePair.appModule.keys)
      if (conflicts.nonEmpty)
        throw new DIAppBootstrapException(
          s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating..."
        )
      if (modulePair.appModule.bindings.isEmpty) {
        throw new DIAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
      }
      ValidatedModulePair(modulePair.bootstrapAutoModule, modulePair.appModule)
    }
  }
}
