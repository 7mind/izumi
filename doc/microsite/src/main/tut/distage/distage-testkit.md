distage-testkit
=======

@@toc { depth=2 }

### Testkit

[distage Example Project](https://github.com/7mind/distage-example) project shows how to use `distage-testkit`, there's also
an overview in the [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.youtube.com/watch?v=CzpvjkUukAs) talk. 

Further documentation still TBD: [Issue #820](https://github.com/7mind/izumi/issues/820)

Some example code from [distage-example](https://github.com/7mind/distage-example):

```scala mdoc:reset:invisible:to-string
type QueryFailure = Throwable

object leaderboard {
  object model {
    type UserId = java.util.UUID
    type Score = Long
  }
  import model._

  object repo {
    trait Ladder[F[_, _]] {
      def submitScore(userId: UserId, score: Score): F[QueryFailure, Unit]
      def getScores: F[QueryFailure, List[(UserId, Score)]]
    }
    trait Profiles[F[_, _]]
  }

  object zioenv {
    import zio.{IO, ZIO, URIO}
    import repo.Ladder
    trait LadderEnv { def ladder: Ladder[IO] }
    trait RndEnv { def rnd: Rnd[IO] }
    object ladder extends Ladder[ZIO[LadderEnv, ?, ?]] {
      def submitScore(userId: UserId, score: Score): ZIO[LadderEnv, QueryFailure, Unit] = ZIO.accessM(_.ladder.submitScore(userId, score))
      def getScores: ZIO[LadderEnv, QueryFailure, List[(UserId, Score)]]                = ZIO.accessM(_.ladder.getScores)
    }
    object rnd extends Rnd[ZIO[RndEnv, ?, ?]] {
      override def apply[A]: URIO[RndEnv, A] = ZIO.accessM(_.rnd.apply[A])
    }
  }
}

object PostgresDockerModule extends distage.ModuleDef

trait Rnd[F[_, _]] {
  def apply[A]: F[Nothing, A]
}
object Rnd {
  final class Impl[F[_, _]] extends Rnd[F] { def apply[A] = ??? }
}
```

```scala mdoc:to-string
import distage.{DIKey, ModuleDef}
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOSpecScalatest}
import izumi.distage.testkit.services.DISyntaxZIOEnv
import leaderboard.model.{Score, UserId}
import leaderboard.repo.{Ladder, Profiles}
import leaderboard.zioenv.{ladder, rnd}
import zio.IO

abstract class LeaderboardTest extends DistageBIOSpecScalatest[IO] with DISyntaxZIOEnv with AssertIO {
  override def config = super.config.copy(
    pluginConfig = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins")),
    moduleOverrides = new ModuleDef {
      make[Rnd[IO]].from[Rnd.Impl[IO]]
      // For testing, setup a docker container with postgres,
      // instead of trying to connect to an external database
      include(PostgresDockerModule)
    },
    // instantiate Ladder & Profiles only once per test-run and
    // share them and all their dependencies across all tests.
    // this includes the Postgres Docker container above and
    // table DDLs 
    memoizationRoots = Set(
      DIKey.get[Ladder[IO]],
      DIKey.get[Profiles[IO]],
    ),
  )
}

trait DummyTest extends LeaderboardTest {
  override final def config = super.config.copy(
    activation = Activation(Repo -> Repo.Dummy),
  )
}

trait ProdTest extends LeaderboardTest {
  override final def config = super.config.copy(
    activation = Activation(Repo -> Repo.Prod),
  )
}

final class LadderTestDummy extends LadderTest with DummyTest
final class LadderTestPostgres extends LadderTest with ProdTest

abstract class LadderTest extends LeaderboardTest {

  "Ladder" should {
    // this test gets dependencies through arguments
    "submit & get" in {
      (rnd: Rnd[IO], ladder: Ladder[IO]) =>
        for {
          user  <- rnd[UserId]
          score <- rnd[Score]
          _     <- ladder.submitScore(user, score)
          res   <- ladder.getScores.map(_.find(_._1 == user).map(_._2))
          _     <- assertIO(res contains score)
        } yield ()
    }

    // other tests get dependencies via ZIO Env:
    "assign a higher position in the list to a higher score" in {
      for {
        user1  <- rnd[UserId]
        score1 <- rnd[Score]
        user2  <- rnd[UserId]
        score2 <- rnd[Score]

        _      <- ladder.submitScore(user1, score1)
        _      <- ladder.submitScore(user2, score2)
        scores <- ladder.getScores

        user1Rank = scores.indexWhere(_._1 == user1)
        user2Rank = scores.indexWhere(_._1 == user2)

        _ <- if (score1 > score2) {
          assertIO(user1Rank < user2Rank)
        } else if (score2 > score1) {
          assertIO(user2Rank < user1Rank)
        }
      } yield ()
    }
  }

}
```

### Integration Checks

Implementation classes that inherit from `izumi.distage.roles.model.IntegrationCheck` can specify a `resourceCheck()` method
that will be called before test instantiation to check if **external test dependencies** (such as docker containers in @ref[distage-framework-docker](distage-framework-docker.md#docker-test-resources))
are available for the test or role. If not, the test will be canceled/ignored.

This feature allows you to e.g. selectively run only the fast in-memory tests that have no external dependencies if you have 
shut down your test environment.

Integration checks are executed only in `distage-testkit` tests and `distage-framework`'s @ref[Roles](distage-framework.md#roles).

Use @scaladoc[StartupPlanExecutor](izumi.distage.roles.services.StartupPlanExecutor) to execute the checks manually.
