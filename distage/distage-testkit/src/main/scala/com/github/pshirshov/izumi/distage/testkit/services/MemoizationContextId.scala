package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import distage.TagK

sealed trait MemoizationContextId

object MemoizationContextId {

  case object Shared extends MemoizationContextId

  case class PerRuntimeAndTagExpr[F[_]](expr: BindingTag.Expressions.Expr, tagK: TagK[F]) extends MemoizationContextId

  case class Custom(id: String) extends MemoizationContextId

}
