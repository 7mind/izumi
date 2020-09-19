package izumi.distage.roles.model.meta

import izumi.distage.model.reflection.DIKey
import izumi.functional.Renderable

final case class RolesInfo(
  requiredComponents: Set[DIKey],
  requiredRoleBindings: Seq[RoleBinding],
  availableRoleNames: Seq[String],
  availableRoleBindings: Seq[RoleBinding],
  unrequiredRoleNames: Set[String],
) {
  @inline def render()(implicit ev: Renderable[RolesInfo]): String = ev.render(this)
}

object RolesInfo {
  implicit val rolesInfoRenderable: Renderable[RolesInfo] = {
    roles =>
      import izumi.fundamentals.platform.strings.IzString._

      val requestedNames = roles.requiredRoleBindings.map(_.descriptor.id)
      roles
        .availableRoleBindings.map {
          r =>
            val active = if (requestedNames.contains(r.descriptor.id)) "[+]" else "[ ]"
            s"$active ${r.descriptor.id}, ${r.binding.key}, source=${r.descriptor.artifact.getOrElse("N/A")}"
        }.sorted.niceList()
  }
}
