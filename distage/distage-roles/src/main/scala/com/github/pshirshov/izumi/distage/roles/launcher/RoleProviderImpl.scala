package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage
import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.distage.roles.roles
import com.github.pshirshov.izumi.distage.roles.roles.{RoleId, RoleService, RoleStarter}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

class RoleProviderImpl(
                        logger: IzLogger,
                        requiredRoles: Set[String],
                        mirrorProvider: MirrorProvider,
                      ) extends RoleProvider {

  def getInfo(bindings: Iterable[Binding]): roles.RolesInfo = {
    val availableBindings = getRoles(bindings)

    val roles = availableRoleNames(availableBindings)

    val enabledRoles = availableBindings
      .filter(b => isEnabledRole(b.tpe))


    distage.roles.roles.RolesInfo(
      Set(
        RuntimeDIUniverse.DIKey.get[RoleStarter]) ++ enabledRoles.map(_.binding.key)
      , enabledRoles
      , roles
      , availableBindings
      , roles.toSet.diff(requiredRoles
      )
    )
  }

  private def availableRoleNames(bindings: Iterable[roles.RoleBinding]): Seq[String] = {
    bindings
      .map(_.tpe)
      .flatMap(r => getAnno(r).toSeq)
      .toSeq
      .distinct
  }

  private def getRoles(bb: Iterable[Binding]): Seq[roles.RoleBinding] = {
    val availableBindings =
      bb
        .map(b => (b, bindingToType(b, isAvailableRoleType)))
        .collect {
          case (b, Some(rt)) =>
            val runtimeClass = mirrorProvider.mirror.runtimeClass(rt.tpe)
            val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
            val annos = getAnno(rt)

            annos.headOption match {
              case Some(value) if annos.size == 1 =>
                Seq(roles.RoleBinding(b, rt, value, src))
              case Some(_) =>
                logger.warn(s"${runtimeClass -> "role"} has multiple identifiers defined: $annos thus ignored")
                Seq.empty
              case None =>
                logger.warn(s"${runtimeClass -> "role"} has no identifier defined thus ignored")
                Seq.empty
            }

        }

    availableBindings.flatten.toSeq
  }

  private def isAvailableRoleType(tpe: SafeType): Boolean = {
    tpe weak_<:< SafeType.get[RoleService]
  }

  private def isEnabledRole(tpe: distage.model.reflection.universe.RuntimeDIUniverse.SafeType): Boolean = {
    val anno: Set[String] = getAnno(tpe)
    isAvailableRoleType(tpe) && (requiredRoles.contains(tpe.tpe.typeSymbol.name.decodedName.toString.toLowerCase) || anno.intersect(requiredRoles).nonEmpty)
  }


  private def bindingToType(b: Binding, pred: RuntimeDIUniverse.SafeType => Boolean): Option[RuntimeDIUniverse.SafeType] = {
    val impldef = b match {
      case s: ImplBinding =>
        Some(s.implementation)

      case _ =>
        None
    }

    impldef
      .map(_.implType)
      .filter(pred)
  }

  private def getAnno(tpe: model.reflection.universe.RuntimeDIUniverse.SafeType): Set[String] = {
    AnnotationTools.collectFirstString[RoleId](RuntimeDIUniverse.u)(tpe.tpe.typeSymbol).toSet
  }
}
