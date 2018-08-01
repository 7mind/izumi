package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage
import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.definition.Binding.ImplBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, mirror}
import com.github.pshirshov.izumi.distage.roles.roles.{RoleAppService, RoleId}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.ClassTag

class RoleProviderImpl(requiredRoles: Set[String]) extends RoleProvider {
  def getInfo(bindings: Iterable[Binding]): RolesInfo = {
    val availableBindings = getRoles(bindings)

    val roles = availableRoleNames(availableBindings)

    val enabledRoles = availableBindings
      .filter(b => isEnabledRole(b.tpe))


    RolesInfo(
      Set(
        RuntimeDIUniverse.DIKey.get[RoleStarter]) ++ enabledRoles.map(_.binding.key)
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
        .collect { case (b, Some(rt)) =>
          val runtimeClass = mirror.runtimeClass(rt.tpe)

          val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
          RoleBinding(b, rt, getAnno(rt), src)
        }

    availableBindings.toSeq
  }

  private def isAvailableRoleType(tpe: SafeType): Boolean = {
    import RuntimeDIUniverse._
    val runtimeClass = mirror.runtimeClass(tpe.tpe)
    classOf[RoleAppService].isAssignableFrom(runtimeClass)

  }

  private def isEnabledRole(tpe: distage.model.reflection.universe.RuntimeDIUniverse.SafeType): Boolean = {
    val runtimeClass = mirror.runtimeClass(tpe.tpe)

    val anno: Set[String] = getAnno(tpe)
    isAvailableRoleType(tpe) && (requiredRoles.contains(runtimeClass.getSimpleName.toLowerCase) || anno.intersect(requiredRoles).nonEmpty)
  }

  private def bindingToType(b: Binding, pred: RuntimeDIUniverse.SafeType => Boolean): Option[RuntimeDIUniverse.SafeType] = {
    val impldef = b match {
      case s: ImplBinding =>
        Some(s.implementation)

      case _ =>
        None
    }


    val implkey = impldef match {
      case Some(i: ImplDef.WithImplType) =>
        Some(i.implType)
      case _ =>
        None
    }

    implkey.filter(pred)
  }

  private def getAnno(tpe: model.reflection.universe.RuntimeDIUniverse.SafeType) = {
    import RuntimeDIUniverse._
    import RuntimeDIUniverse.u._

    def findArgument(ann: Annotation): Option[String] = {
      AnnotationTools.findArgument(ann) {
        case Literal(Constant(str: String)) =>
          str
      }
    }

    val anno = SymbolInfo(tpe.tpe.typeSymbol, tpe).findAnnotation(SafeType.get[RoleId]).flatMap(findArgument).toSet
    anno
  }
}
