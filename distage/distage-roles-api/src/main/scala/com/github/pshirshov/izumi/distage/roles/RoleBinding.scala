package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact

case class RoleBinding(binding: Binding, runtimeClass: Class[_], tpe: SafeType, name: String, source: Option[IzArtifact])
