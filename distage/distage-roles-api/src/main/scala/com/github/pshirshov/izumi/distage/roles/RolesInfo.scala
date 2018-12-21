package com.github.pshirshov.izumi.distage.roles

import distage.DIKey

case class RolesInfo(
                      requiredComponents: Set[DIKey]
                      , requiredRoleBindings: Seq[RoleBinding]
                      , availableRoleNames: Seq[String]
                      , availableRoleBindings: Seq[RoleBinding]
                      , unrequiredRoleNames: Set[String]
                    )
