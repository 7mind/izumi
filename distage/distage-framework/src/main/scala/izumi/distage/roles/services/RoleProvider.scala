package izumi.distage.roles.services

import distage._
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.Binding.ImplBinding
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import izumi.distage.roles.meta
import izumi.distage.roles.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{AbstractRole, RoleDescriptor}
import izumi.fundamentals.platform.resources.IzManifest
import izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

trait RoleProvider[F[_]] {
  def getInfo(bindings: Seq[Binding]): RolesInfo
}

object RoleProvider {

  class Impl[F[_]: TagK]
  (
    logger: IzLogger,
    requiredRoles: Set[String],
  ) extends RoleProvider[F] {

    def getInfo(bindings: Seq[Binding]): RolesInfo = {
      val availableBindings = getRoles(bindings)

      val roles = availableBindings.map(_.descriptor.id)

      val enabledRoles = availableBindings.filter(isRoleEnabled)

      meta.RolesInfo(
        enabledRoles.map(_.binding.key).toSet,
        enabledRoles,
        roles,
        availableBindings,
        roles.toSet.diff(requiredRoles),
      )
    }

    private[this] def getRoles(bb: Seq[Binding]): Seq[RoleBinding] = {
      bb.flatMap {
        b =>
          b match {
            case s: ImplBinding if isRoleType(s.implementation.implType) =>
              Seq((b, s.implementation.implType))

            case _ =>
              Seq.empty
          }
      }.flatMap {
        case (roleBinding, impltype) =>
          getDescriptor(roleBinding.key.tpe) match {
            case Some(d) =>
              val runtimeClass = roleBinding.key.tpe.cls
              val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
              Seq(RoleBinding(roleBinding, runtimeClass, impltype, d, src))
            case None =>
              logger.error(s"${roleBinding.key -> "role"} defined ${roleBinding.origin -> "at"} has no companion object inherited from RoleDescriptor thus ignored")
              Seq.empty
          }
      }
    }

    private[this] def isRoleEnabled(b: RoleBinding): Boolean = {
      requiredRoles.contains(b.descriptor.id) || requiredRoles.contains(b.tpe.tag.shortName.toLowerCase)
    }

    private[this] def isRoleType(tpe: SafeType): Boolean = {
      tpe <:< SafeType.get[AbstractRole[F]]
    }

    // FIXME: Scala.js RoleDescriptor instantiation (portable-scala-reflect) ???
    private[this] def getDescriptor(role: SafeType): Option[RoleDescriptor] = {
      val roleClassName = role.cls.getCanonicalName
      try {
        Some(Class.forName(s"$roleClassName$$").getField("MODULE$").get(null).asInstanceOf[RoleDescriptor])
      } catch {
        case t: Throwable =>
          logger.error(s"Failed to reflect descriptor for $role: $t")
          None
      }
    }

  }

}
