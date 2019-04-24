package com.github.pshirshov.izumi.distage.roles.services

import _root_.distage._
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.reflection.universe.MirrorProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.model.{AbstractRoleF, RoleDescriptor, meta}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

class RoleProviderImpl[F[_] : TagK](
                                     logger: IzLogger,
                                     requiredRoles: Set[String],
                                     mirrorProvider: MirrorProvider,
                                   ) extends RoleProvider[F] {

  def getInfo(bindings: Seq[Binding]): RolesInfo = {
    val availableBindings = getRoles(bindings)

    val roles = availableBindings.map(_.descriptor.id)

    val enabledRoles = availableBindings
      .filter(b => isEnabledRole(b))

    meta.RolesInfo(
      enabledRoles.map(_.binding.key).toSet
      , enabledRoles
      , roles
      , availableBindings
      , roles.toSet.diff(requiredRoles
      )
    )
  }

  private def getRoles(bb: Seq[Binding]): Seq[RoleBinding] = {
    bb
      .flatMap {
        b =>
          b match {
            case s: ImplBinding if isAvailableRoleType(s.implementation.implType) =>
              Seq((b, s.implementation.implType))

            case _ =>
              Seq.empty
          }
      }
      .flatMap {
        case (rb, impltype) =>
          getDescriptor(rb.key.tpe) match {
            case Some(d) =>
              val runtimeClass = mirrorProvider.mirror.runtimeClass(impltype.tpe)
              val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
              Seq(RoleBinding(rb, runtimeClass, impltype, d, src))
            case None =>
              logger.error(s"${rb.key -> "role"} defined ${rb.origin -> "at"} has no companion object inherited from RoleDescriptor thus ignored")
              Seq.empty
          }

      }
  }

  private def isEnabledRole(b: RoleBinding): Boolean = {
    requiredRoles.contains(b.descriptor.id) || requiredRoles.contains(b.tpe.tpe.typeSymbol.name.decodedName.toString.toLowerCase)
  }

  private def isAvailableRoleType(tpe: SafeType): Boolean = {
    tpe weak_<:< SafeType.get[AbstractRoleF[F]]
  }

  private def getDescriptor(role: SafeType): Option[RoleDescriptor] = {
    try {
      mirrorProvider.mirror.reflectModule(role.tpe.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
        case rd: RoleDescriptor => Some(rd)
        case _ => None
      }
    } catch {
      case t: Throwable =>
        logger.error(s"Failed to reflect descriptor for $role: $t")
        None
    }
  }

}
