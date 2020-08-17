package leaderboard

import distage.Activation
import distage.plugins.PluginConfig
import izumi.distage.model.definition.Binding
import izumi.distage.model.reflection.SafeType
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.bundled.{ConfigWriter, Help}
import izumi.distage.roles.launcher.RoleAppLauncher
import izumi.distage.roles.launcher.services.RoleProvider
import izumi.distage.roles.model.RoleDescriptor
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.logstage.api.IzLogger
import zio.IO

/**
  * Generic launcher not set to run a specific role by default,
  * use command-line arguments to choose one or multiple roles:
  *
  * {{{
  *
  *   # launch app with prod repositories
  *
  *   ./launcher :leaderboard
  *
  *   # launch app with dummy repositories
  *
  *   ./launcher -u repo:dummy :leaderboard
  *
  *   # launch just the ladder API, without profiles
  *
  *   ./launcher :ladder
  *
  *   # display help
  *
  *   ./launcher :help
  *
  *   # write configs in HOCON format to ./default-configs
  *
  *   ./launcher :configwriter -format hocon -t default-configs
  *
  *   # print help, dump configs and launch app with dummy repositories
  *
  *   ./launcher -u repo:dummy :help :configwriter :leaderboard
  *
  * }}}
  */
object GenericLauncher extends MainBase(Activation.empty) {
  override val requiredRoles = Vector.empty
}

sealed abstract class MainBase(
  activation: Activation,
  override val requiredRoles: Vector[RawRoleParams] = Vector(RawRoleParams(LeaderboardRole.id)),
) extends RoleAppMain.Default(
    launcher = new RoleAppLauncher.LauncherBIO[zio.IO] {
//      override val pluginConfig = PluginConfig.cached("leaderboard.plugins")
      override val pluginConfig = PluginConfig.staticallyAvailablePlugins("leaderboard.plugins")
      override val requiredActivations = activation

      override protected def makeRoleProvider(logger: IzLogger, activeRoleNames: Set[String]): RoleProvider[zio.IO[Throwable, *]] = {
        new RoleProvider.Impl[zio.IO[Throwable, *]](logger, activeRoleNames) {
          override protected def getDescriptor(role: SafeType): Option[RoleDescriptor] = {
            if (role == SafeType.get[LeaderboardRole[zio.IO]]) {
              Some(LeaderboardRole)
            } else if (role == SafeType.get[LadderRole[zio.IO]]) {
              Some(LadderRole)
            } else if (role == SafeType.get[ProfileRole[zio.IO]]) {
              Some(ProfileRole)
            } else if (role == SafeType.get[ConfigWriter[zio.IO[Throwable, *]]]) {
              Some(ConfigWriter)
            } else if (role == SafeType.get[Help[zio.IO[Throwable, *]]]) {
              Some(Help)
            } else {
              None
            }

          }
        }

      }
    }
  )
