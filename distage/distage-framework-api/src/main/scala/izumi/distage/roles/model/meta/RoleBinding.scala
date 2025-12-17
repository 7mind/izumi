package izumi.distage.roles.model.meta

import izumi.distage.model.definition.Binding
import izumi.distage.model.reflection.*
import izumi.distage.roles.model.RoleDescriptor

final case class RoleBinding(
  binding: Binding,
  implType: SafeType,
  descriptor: RoleDescriptor,
) {
  val id: String = descriptor.id
}
