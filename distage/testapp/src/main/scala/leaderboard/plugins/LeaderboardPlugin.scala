package leaderboard.plugins

import distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import izumi.distage.roles.bundled.BundledRolesModule
import izumi.distage.roles.model.definition.RoleModuleDef
import leaderboard.api.{HttpApi, LadderApi, ProfileApi}
import leaderboard.http.HttpServer
import leaderboard.repo.{Ladder, Profiles, Ranks}
import leaderboard.{LadderRole, LeaderboardRole, ProfileRole}
import org.http4s.dsl.Http4sDsl
import zio.IO

object LeaderboardPlugin extends PluginDef {
  include(modules.roles[IO])
  include(modules.api[IO])
  include(modules.repoDummy[IO])

  object modules {
    def roles[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef with RoleModuleDef {
      // The `ladder` role
      makeRole[LadderRole[F]]

      // The `profile` role
      makeRole[ProfileRole[F]]

      // The composite `leaderboard` role that pulls in both `ladder` & `profile` roles
      makeRole[LeaderboardRole[F]]

      // Add bundled roles: `help` & `configwriter`
      include(BundledRolesModule[F[Throwable, ?]](version = "1.0.0-SNAPSHOT"))
    }

    def api[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      // The `ladder` API
      make[LadderApi[F]]
      // The `profile` API
      make[ProfileApi[F]]

      // A set of all APIs
      many[HttpApi[F]]
        .weak[LadderApi[F]] // add ladder API as a _weak reference_
        .weak[ProfileApi[F]] // add profiles API as a _weak reference_

      make[HttpServer[F]].fromResource[HttpServer.Impl[F]]

      make[Ranks[F]].from[Ranks.Impl[F]]

      make[Http4sDsl[F[Throwable, ?]]]
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[Ladder[F]].fromResource[Ladder.Dummy[F]]
      make[Profiles[F]].fromResource[Profiles.Dummy[F]]
    }

  }
}
