package com.github.pshirshov.izumi.distage.roles.model.meta

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.roles.model.RoleDescriptor

case class RoleBinding(
                        binding: Binding,
                        runtimeClass: Class[_],
                        tpe: SafeType,
                        descriptor: RoleDescriptor,
                        source: Option[IzArtifact],
                      )
