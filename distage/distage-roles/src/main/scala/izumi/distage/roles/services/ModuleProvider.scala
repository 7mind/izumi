package com.github.pshirshov.izumi.distage.roles.services

import distage._

trait ModuleProvider[F[_]] {
  def bootstrapModules(): Seq[BootstrapModuleDef]

  def appModules(): Seq[Module]

}
