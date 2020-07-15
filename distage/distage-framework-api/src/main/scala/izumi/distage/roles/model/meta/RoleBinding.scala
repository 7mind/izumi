package izumi.distage.roles.model.meta

import izumi.distage.model.definition.Binding
import izumi.distage.model.reflection._
import izumi.distage.roles.model.RoleDescriptor
import izumi.fundamentals.platform.resources.IzArtifact

final case class RoleBinding(
  binding: Binding,
  runtimeClass: Class[_],
  tpe: SafeType,
  descriptor: RoleDescriptor,
  source: Option[IzArtifact],
)
