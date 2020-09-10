package izumi.distage.roles.launcher

import distage.{Id, _}
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.Binding.ImplBinding
import izumi.distage.model.reflection.SafeType
import izumi.distage.plugins.PluginBase
import izumi.distage.roles.model.definition.RoleTag
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{AbstractRole, RoleDescriptor}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.jvm.IzJvm
import izumi.fundamentals.platform.resources.IzManifest
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import scala.collection.immutable.Set
import scala.reflect.ClassTag

trait RoleProvider[F[_]] {
  def loadRoles(): RolesInfo
  //def getInfo(bindings: Seq[Binding], requiredRoles: Set[String]): RolesInfo
}

object RoleProvider {

  class Impl[F[_]: TagK](
    logger: IzLogger,
    reflectionEnabled: Boolean @Id("distage.roles.reflection"),
    parameters: RawAppArgs,
    bsPlugins: Seq[PluginBase] @Id("bootstrap"),
    appPlugins: Seq[PluginBase] @Id("main"),
  ) extends RoleProvider[F] {

    def loadRoles(): RolesInfo = {
      val bindings = appPlugins.flatMap(_.bindings)
      val bsBindings = bsPlugins.flatMap(_.bindings)
      logger.info(
        s"Available ${appPlugins.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${bsPlugins.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ..."
      )

      val activeRoleNames = parameters.roles.map(_.role).toSet
      val roles = this.getInfo(bindings, activeRoleNames)

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

    protected def getInfo(bindings: Seq[Binding], requiredRoles: Set[String]): RolesInfo = {
      val availableBindings = getRoles(bindings)

      val roles = availableBindings.map(_.descriptor.id)

      val enabledRoles = availableBindings.filter(isRoleEnabled(requiredRoles))

      RolesInfo(
        enabledRoles.map(_.binding.key).toSet,
        enabledRoles,
        roles,
        availableBindings,
        roles.toSet.diff(requiredRoles),
      )
    }

    private def reflectionEnabled(): Boolean = {
      reflectionEnabled && !IzJvm.isGraalNativeImage()
    }

    private[this] def getRoles(bb: Seq[Binding]): Seq[RoleBinding] = {
      bb.flatMap {
        case s: ImplBinding if s.tags.exists(_.isInstanceOf[RoleTag]) =>
          s.tags.collect {
            case RoleTag(roleDescriptor) => Right((s, roleDescriptor))
          }

        case s: ImplBinding if isRoleType(s.implementation.implType) =>
          Seq(Left(s))

        case _ =>
          Seq.empty
      }.flatMap {
          case Right((roleBinding, descriptor)) => mkRoleBinding(roleBinding, descriptor)
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
    }

    private[this] def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
      requiredRoles.contains(b.descriptor.id) || requiredRoles.contains(b.tpe.tag.shortName.toLowerCase)
    }

    private[this] def isRoleType(tpe: SafeType): Boolean = {
      tpe <:< SafeType.get[AbstractRole[F]]
    }

    private[this] def mkRoleBinding(roleBinding: ImplBinding, roleDescriptor: RoleDescriptor): Seq[RoleBinding] = {
      val impltype = roleBinding.implementation.implType
      val runtimeClass = roleBinding.key.tpe.cls
      val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
      Seq(RoleBinding(roleBinding, runtimeClass, impltype, roleDescriptor, src))
    }

    // FIXME: Scala.js RoleDescriptor instantiation (portable-scala-reflect) ???
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
