package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact
import distage.SafeType

case class RoleBinding(binding: Binding, tpe: SafeType, anno: Set[String], source: Option[IzArtifact])
