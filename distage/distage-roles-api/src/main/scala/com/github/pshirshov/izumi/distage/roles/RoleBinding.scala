package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact
import distage.SafeType

case class RoleBinding(binding: Binding, tpe: SafeType, name: String, source: Option[IzArtifact])
