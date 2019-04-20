package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

case class RolesInfo(
                      requiredComponents: Set[DIKey]
                      , requiredRoleBindings: Seq[RoleBinding]
                      , availableRoleNames: Seq[String]
                      , availableRoleBindings: Seq[RoleBinding]
                      , unrequiredRoleNames: Set[String]
                    )
