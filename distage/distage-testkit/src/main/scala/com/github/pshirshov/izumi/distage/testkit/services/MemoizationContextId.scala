package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisMember
import com.github.pshirshov.izumi.distage.model.definition.AxisBase
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import distage.SafeType

sealed trait MemoizationContextId

object MemoizationContextId {

  case object Shared extends MemoizationContextId

  case class PerRuntimeAndActivationAndBsconfig[F[_]](bootstrapConfig: BootstrapConfig, activation: Map[AxisBase, AxisMember], fType: SafeType) extends MemoizationContextId

  case class Custom(id: String) extends MemoizationContextId

}
