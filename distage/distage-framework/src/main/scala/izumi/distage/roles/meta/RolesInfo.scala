package izumi.distage.roles.meta

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

final case class RolesInfo(
                            requiredComponents: Set[DIKey],
                            requiredRoleBindings: Seq[RoleBinding],
                            availableRoleNames: Seq[String],
                            availableRoleBindings: Seq[RoleBinding],
                            unrequiredRoleNames: Set[String],
                          )
