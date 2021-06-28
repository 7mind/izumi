package izumi.distage.roles.model.meta

import izumi.distage.model.definition.Binding
import izumi.distage.model.reflection._
import izumi.distage.roles.model.RoleDescriptor

final case class RoleBinding(
  binding: Binding,
  runtimeClass: Class[?],
  tpe: SafeType,
  descriptor: RoleDescriptor,
)
