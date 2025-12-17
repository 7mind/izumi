package izumi.distage.roles.launcher

import distage.*
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.Binding.ImplBinding
import izumi.distage.model.reflection.SafeType
import izumi.distage.roles.model.definition.RoleTag
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{AbstractRole, RoleDescriptor}
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import scala.annotation.unused

trait RoleProvider {
  def loadRoles[F[_]: TagK](appModule: ModuleBase): RolesInfo
}

object RoleProvider {

  open class NonReflectiveImpl(
    logger: IzLogger @Id("early"),
    ignoreMismatchedEffect: Boolean @Id("distage.roles.ignore-mismatched-effect"),
    parameters: RoleAppArgs,
  ) extends RoleProvider {

    def loadRoles[F[_]: TagK](appModule: ModuleBase): RolesInfo = {
      val rolesInfo = getInfo(
        bindings = appModule.bindings,
        requiredRoles = parameters.roles.iterator.map(_.role).toSet,
        roleType = SafeType.get[AbstractRole[F]],
      )
      logger.info(s"Available ${rolesInfo.render() -> "roles"}")
      rolesInfo
    }

    protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
      val availableRoleBindings = findRoleBindings(bindings, roleType)
      val requiredRoleBindings = availableRoleBindings.filter(isRoleEnabled(requiredRoles))

      val roleNames = availableRoleBindings.map(_.id)
      val requiredRoleNames = requiredRoleBindings.iterator.map(_.id).toSet
      val unrequiredRoleNames = roleNames.diff(requiredRoleNames)

      val rolesInfo = RolesInfo(
        requiredComponents = requiredRoleBindings.iterator.map(_.binding.key).toSet,
        requiredRoleBindings = requiredRoleBindings,
        requiredRoleNames = requiredRoleNames,
        availableRoleNames = roleNames,
        availableRoleBindings = availableRoleBindings,
        unrequiredRoleNames = unrequiredRoleNames,
      )

      val missing = requiredRoles.diff(availableRoleBindings.map(_.id))
      if (missing.nonEmpty) {
        logger.crit(s"Missing ${missing.niceList() -> "roles"}")
        throw new DIAppBootstrapException(s"""Unknown roles:${missing.niceList("    ")}
                                             |
                                             |Available roles:${rolesInfo.render()}""".stripMargin)
      }
      if (requiredRoleBindings.isEmpty) {
        throw new DIAppBootstrapException(s"""No roles selected to launch, please select one of the following roles using syntax `:${'$'}roleName` on the command-line.
                                             |
                                             |Available roles:${rolesInfo.render()}""".stripMargin)
      }

      rolesInfo
    }

    protected def findRoleBindings(bindings: Set[Binding], roleType: SafeType): Set[RoleBinding] = {
      bindings.collect {
        case s: ImplBinding if s.tags.exists(_.isInstanceOf[RoleTag]) && checkRoleType(s.implementation.implType, roleType, log = !ignoreMismatchedEffect) =>
          mkRoleBinding(s, s.tags.collectFirst { case RoleTag(roleDescriptor) => roleDescriptor }.get)

        case s: ImplBinding if s.implementation.implType <:< roleType && !s.isMutator =>
          handleMissingStaticMetadata(roleType, s)
      }
    }

    protected def handleMissingStaticMetadata(@unused roleType: SafeType, s: ImplBinding): RoleBinding = {
      logger.crit(s"${s.key -> "role"} defined ${s.origin -> "at"} has no RoleDescriptor, companion reflection is disabled")
      throw new DIAppBootstrapException(s"role=${s.key} defined at=${s.origin} has no RoleDescriptor, companion reflection is disabled")
    }

    protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
      requiredRoles.contains(b.id)
    }

    protected def checkRoleType(implType: SafeType, roleType: SafeType, log: Boolean): Boolean = {
      val isCompatible = implType <:< roleType
      if (!isCompatible && log) logger.warn(s"Found role binding with incompatible effect type $implType (expected to be a subtype of $roleType)")
      isCompatible
    }

    protected def mkRoleBinding(roleBinding: ImplBinding, roleDescriptor: RoleDescriptor): RoleBinding = {
      val implType = roleBinding.implementation.implType
      RoleBinding(roleBinding, implType, roleDescriptor)
    }
  }

}
