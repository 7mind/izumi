package com.github.pshirshov.izumi.distage.roles.roles

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

case class RoleBinding(binding: Binding, tpe: SafeType, name: String, source: Option[IzArtifact])
