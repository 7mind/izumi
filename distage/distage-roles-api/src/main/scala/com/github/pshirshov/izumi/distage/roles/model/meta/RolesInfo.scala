package com.github.pshirshov.izumi.distage.roles.model.meta

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

case class RolesInfo(
                      requiredComponents: Set[RuntimeDIUniverse.DIKey]
                      , requiredRoleBindings: Seq[RoleBinding]
                      , availableRoleNames: Seq[String]
                      , availableRoleBindings: Seq[RoleBinding]
                      , unrequiredRoleNames: Set[String]
                    )
