package izumi.distage.roles.model.meta

import izumi.distage.model.definition.Binding
import izumi.fundamentals.platform.resources.IzArtifact
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.roles.model.RoleDescriptor

case class RoleBinding(
                        binding: Binding,
                        runtimeClass: Class[_],
                        tpe: SafeType,
                        descriptor: RoleDescriptor,
                        source: Option[IzArtifact],
                      )
