package com.github.pshirshov.test3.bootstrap

import distage.plugins.BootstrapPluginDef
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.{Activation, BootstrapModule, Id}
import izumi.distage.model.provisioning.strategies.EffectStrategy
import izumi.distage.planning.solver.PlanSolver

object BootstrapFixture3 {

  final case class BasicConfig(a: Boolean, b: Int)

  object BootstrapPlugin extends BootstrapPluginDef() with ConfigModuleDef {
    makeConfig[BasicConfig]("basicConfig")
    make[BootstrapComponent]
  }

  final class UnsatisfiedDep

  final case class BootstrapComponent(
    effectStrategy: EffectStrategy,
    planSolver: PlanSolver,
    activation: Activation @Id("bootstrapActivation"),
    bsModule: BootstrapModule,
  )

}
