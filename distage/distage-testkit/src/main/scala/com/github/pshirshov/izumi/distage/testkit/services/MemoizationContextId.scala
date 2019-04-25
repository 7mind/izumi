package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import distage.SafeType

sealed trait MemoizationContextId

object MemoizationContextId {

  case object Shared extends MemoizationContextId

  case class PerRuntimeAndTagsAndBsconfig[F[_]](bootstrapConfig: BootstrapConfig, expr: BindingTag.Expressions.Expr, fType: SafeType) extends MemoizationContextId

  case class Custom(id: String) extends MemoizationContextId

}
