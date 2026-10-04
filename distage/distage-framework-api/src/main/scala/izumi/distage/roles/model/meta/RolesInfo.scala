package izumi.distage.roles.model.meta

import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable

import scala.collection.immutable.ArraySeq
import izumi.fundamentals.platform.strings.IzString.*

final case class RolesInfo(
  requiredComponents: Set[DIKey],
  requiredRoleBindings: Set[RoleBinding],
  requiredRoleNames: Set[String],
  availableRoleNames: Set[String],
  availableRoleBindings: Set[RoleBinding],
  unrequiredRoleNames: Set[String],
) {
  @inline def render()(implicit ev: Renderable[RolesInfo]): String = ev.render(this)
}

object RolesInfo {
  implicit val rolesInfoRenderable: Renderable[RolesInfo] = {
    roles =>

      val requestedNames = roles.requiredRoleBindings.map(_.id)
      ArraySeq
        .unsafeWrapArray {
          roles.availableRoleBindings.iterator.map {
            r =>
              val active = if (requestedNames.contains(r.id)) "[+]" else "[ ]"
              s"$active ${r.id}, ${r.binding.key}, source=${r.descriptor.artifact.getOrElse("N/A")}"
          }.toArray
        }.sorted.niceList()
  }
}
