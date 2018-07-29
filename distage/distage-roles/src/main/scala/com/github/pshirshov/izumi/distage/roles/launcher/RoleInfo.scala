package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzArtifact

case class RoleBinding(binding: Binding, tpe: SafeType, anno: Set[String], source: Option[IzArtifact])

case class RoleInfo(
                     requiredComponents: Set[izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey]
                     , requiredRoleBindings: Seq[RoleBinding]
                     , availableRoleNames: Seq[String]
                     , availableRoleBindings: Seq[RoleBinding]
                     , unrequiredRoleNames: Set[String]
                   )
