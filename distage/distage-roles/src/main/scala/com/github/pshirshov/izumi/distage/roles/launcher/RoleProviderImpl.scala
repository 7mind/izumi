package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage
import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools
import com.github.pshirshov.izumi.logstage.api.IzLogger
import _root_.distage._

import scala.reflect.ClassTag

class RoleProviderImpl[F[_] : TagK](
                                     logger: IzLogger,
                                     requiredRoles: Set[String],
                                     mirrorProvider: MirrorProvider,
                                   ) extends RoleProvider[F] {

  def getInfo(bindings: Iterable[Binding]): RolesInfo = {
    val availableBindings = getRoles(bindings)

    val roles = availableRoleNames(availableBindings)

    val enabledRoles = availableBindings
      .filter(b => isEnabledRole(b.tpe))


    RolesInfo(
        enabledRoles.map(_.binding.key).toSet
        , enabledRoles
        , roles
        , availableBindings
        , roles.toSet.diff(requiredRoles
        )
      )
  }

  private def availableRoleNames(bindings: Iterable[RoleBinding]): Seq[String] = {
    bindings
      .map(_.tpe)
      .flatMap(r => getAnno(r).toSeq)
      .toSeq
      .distinct
  }

  private def getRoles(bb: Iterable[Binding]): Seq[RoleBinding] = {
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
                Seq(RoleBinding(b, runtimeClass, rt, value, src))
              case Some(_) =>
                logger.error(s"${runtimeClass -> "role"} has multiple identifiers defined: $annos thus ignored")
                Seq.empty
              case None =>
                logger.error(s"${runtimeClass -> "role"} has no identifier defined thus ignored")
                Seq.empty
            }

        }

    availableBindings.flatten.toSeq
  }

  private def isEnabledRole(tpe: distage.model.reflection.universe.RuntimeDIUniverse.SafeType): Boolean = {
    val anno: Set[String] = getAnno(tpe)
    isAvailableRoleType(tpe) && (requiredRoles.contains(tpe.tpe.typeSymbol.name.decodedName.toString.toLowerCase) || anno.intersect(requiredRoles).nonEmpty)
  }

  private def isAvailableRoleType(tpe: SafeType): Boolean = {
    (tpe weak_<:< SafeType.get[RoleService]) || (tpe weak_<:< SafeType.get[RoleTask2[F]]) || (tpe weak_<:< SafeType.get[RoleService2[F]])
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

  private def getAnno(tpe: SafeType): Set[String] = {
    AnnotationTools.collectFirstString[RoleId](RuntimeDIUniverse.u)(tpe.tpe.typeSymbol).toSet
  }
}
