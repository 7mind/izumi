package com.github.pshirshov.izumi.distage.roles.role2

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module}

trait ModuleProvider {
  def bootstrapModules(): Seq[BootstrapModuleDef]

  def appModules(): Seq[Module]

}
