package izumi.distage.roles.launcher

import distage.{Id, _}
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.Binding.ImplBinding
import izumi.distage.model.reflection.SafeType
import izumi.distage.roles.model.definition.RoleTag
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{AbstractRole, RoleDescriptor}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.jvm.IzJvm
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import scala.collection.immutable.Set

trait RoleProvider {
  def loadRoles[F[_]: TagK](appModule: ModuleBase): RolesInfo
}

object RoleProvider {

  class Impl(
    logger: IzLogger,
    reflectionEnabled: Boolean @Id("distage.roles.reflection"),
    parameters: RawAppArgs,
  ) extends RoleProvider {

    def loadRoles[F[_]: TagK](appModule: ModuleBase): RolesInfo = {
      val roleType = SafeType.get[AbstractRole[F]]
      val bindings = appModule.bindings
      val roles = {
        val activeRoleNames = parameters.roles.map(_.role).toSet
        getInfo(bindings, activeRoleNames, roleType)
      }

      logger.info(s"Available ${roles.render() -> "roles"}")

      val missing = parameters.roles.map(_.role).toSet.diff(roles.availableRoleBindings.map(_.descriptor.id).toSet)
      if (missing.nonEmpty) {
        logger.crit(s"Missing ${missing.niceList() -> "roles"}")
        throw new DIAppBootstrapException(s"Unknown roles: $missing")
      }
      if (roles.requiredRoleBindings.isEmpty) {
        throw new DIAppBootstrapException(s"""No roles selected to launch, please select one of the following roles using syntax `:${'$'}roleName` on the command-line.
                                             |
                                             |Available roles: ${roles.render()}""".stripMargin)
      }

      roles
    }

    protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
      val availableRoleBindings = instantiateRoleBindings(bindings, roleType)
      val enabledRoleBindings = availableRoleBindings.filter(isRoleEnabled(requiredRoles))
      val roleNames = availableRoleBindings.map(_.descriptor.id).toSet

      RolesInfo(
        requiredComponents = enabledRoleBindings.iterator.map(_.binding.key).toSet,
        requiredRoleBindings = enabledRoleBindings,
        availableRoleNames = roleNames,
        availableRoleBindings = availableRoleBindings,
        unrequiredRoleNames = roleNames.toSet.diff(enabledRoleBindings.iterator.map(_.descriptor.id).toSet),
      )
    }

    protected def instantiateRoleBindings(bb: Set[Binding], roleType: SafeType): Seq[RoleBinding] = {
      bb.iterator
        .flatMap {
          case s: ImplBinding if s.tags.exists(_.isInstanceOf[RoleTag]) =>
            s.tags.collect {
              case RoleTag(roleDescriptor) =>
                Right((s, roleDescriptor))
            }

          case s: ImplBinding if s.implementation.implType <:< roleType =>
            Seq(Left(s))

          case _ =>
            Seq.empty
        }
        .map {
          case Right((roleBinding, descriptor)) =>
            mkRoleBinding(roleBinding, descriptor)

          case Left(roleBinding) =>
            if (reflectionEnabled()) {
              reflectCompanionDescriptor(roleBinding.key.tpe) match {
                case Some(descriptor) =>
                  logger.warn(
                    s"""${roleBinding.key -> "role"} defined ${roleBinding.origin -> "at"}: using deprecated reflective look-up of `RoleDescriptor` companion object.
                       |Please use `RoleModuleDef` & `makeRole` to create a role binding explicitly, instead.""".stripMargin
                  )
                  mkRoleBinding(roleBinding, descriptor)
                case None =>
                  logger.crit(s"${roleBinding.key -> "role"} defined ${roleBinding.origin -> "at"} has no companion object inherited from RoleDescriptor")
                  throw new DIAppBootstrapException(s"role=${roleBinding.key} defined at=${roleBinding.origin} has no companion object inherited from RoleDescriptor")
              }
            } else {
              logger.crit(s"${roleBinding.key -> "role"} defined ${roleBinding.origin -> "at"} has no RoleDescriptor, companion reflection is disabled")
              throw new DIAppBootstrapException(s"role=${roleBinding.key} defined at=${roleBinding.origin} has no RoleDescriptor, companion reflection is disabled")
            }
        }
        .toSeq
    }

    protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
      requiredRoles.contains(b.descriptor.id) || requiredRoles.contains(b.tpe.tag.shortName.toLowerCase)
    }

    protected final def reflectionEnabled(): Boolean = {
      reflectionEnabled && !IzJvm.isGraalNativeImage()
    }

    protected final def mkRoleBinding(roleBinding: ImplBinding, roleDescriptor: RoleDescriptor): RoleBinding = {
      val runtimeClass = roleBinding.key.tpe.cls
      val implType = roleBinding.implementation.implType
      RoleBinding(roleBinding, runtimeClass, implType, roleDescriptor)
    }

    // FIXME: Scala.js RoleDescriptor instantiation (portable-scala-reflect) ??? (Not that relevant anymore with RoleModuleDef...)
    protected def reflectCompanionDescriptor(role: SafeType): Option[RoleDescriptor] = {
      val roleClassName = role.cls.getName
      try {
        Some(Class.forName(s"$roleClassName$$").getField("MODULE$").get(null).asInstanceOf[RoleDescriptor])
      } catch {
        case t: Throwable =>
          logger.crit(s"""Failed to reflect RoleDescriptor companion object for $role: $t
                         |Please create a companion object extending `izumi.distage.roles.model.RoleDescriptor` for `$roleClassName`
                         |""".stripMargin)
          None
      }
    }

  }

}
