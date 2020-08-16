package leaderboard.plugins

import distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import izumi.distage.roles.bundled.BundledRolesModule
import leaderboard.repo.{Ladder, Profiles, Ranks}
import leaderboard.{LadderRole, LeaderboardRole, ProfileRole}
import zio.IO

object LeaderboardPlugin extends PluginDef {
  include(modules.roles[IO])
  include(modules.api[IO])
  include(modules.repoDummy[IO])

  object modules {
    def roles[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      // The `ladder` role
      make[LadderRole[F]]

      // The `profile` role
      make[ProfileRole[F]]

      // The composite `leaderboard` role that pulls in both `ladder` & `profile` roles
      make[LeaderboardRole[F]]

      // Add bundled roles: `help` & `configwriter`
      include(BundledRolesModule[F[Throwable, ?]](version = "1.0.0-SNAPSHOT"))
    }

    def api[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[Ranks[F]].from[Ranks.Impl[F]]
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[Ladder[F]].fromResource[Ladder.Dummy[F]]
      make[Profiles[F]].fromResource[Profiles.Dummy[F]]
    }

  }
}
