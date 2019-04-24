package com.github.pshirshov.izumi.distage.roles.model

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

case class RoleBinding(binding: Binding, runtimeClass: Class[_], tpe: SafeType, name: String, source: Option[IzArtifact])
