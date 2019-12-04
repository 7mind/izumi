distage-testkit
=======

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

[distage Livecode project](https://github.com/7mind/distage-livecode) project shows several examples of `distage-testkit` usage:

```scala
package livecode

import distage.{DIKey, ModuleDef}
import doobie.util.transactor.Transactor
import izumi.distage.model.definition.StandardAxis
import izumi.distage.testkit.integration.docker.examples.PostgresDocker
import izumi.distage.testkit.services.DISyntaxZIOEnv
import izumi.distage.testkit.services.st.dtest.TestConfig
import izumi.distage.testkit.st.specs.DistageBIOSpecScalatest
import livecode.code._
import livecode.zioenv._
import zio.{IO, Task, ZIO}

abstract class LivecodeTest extends DistageBIOSpecScalatest[IO] with DISyntaxZIOEnv {
  override def config = TestConfig(
    pluginPackages = Some(Seq("livecode.plugins")),
    activation     = StandardAxis.testProdActivation,
    moduleOverrides = new ModuleDef {
      make[Rnd[IO]].from[Rnd.Impl[IO]]
      include(PostgresDockerModule)
    },
    memoizedKeys = Set(
      DIKey.get[Transactor[Task]],
      DIKey.get[Ladder[IO]],
      DIKey.get[Profiles[IO]],
      DIKey.get[PostgresDocker.Container],
    ),
  )
}

trait DummyTest extends LivecodeTest {
  override final def config = super.config.copy(
    activation = StandardAxis.testDummyActivation,
  )
}

final class LadderTestDummy extends LadderTest with DummyTest
final class ProfilesTestDummy extends ProfilesTest with DummyTest
final class RanksTestDummy extends RanksTest with DummyTest

class LadderTest extends LivecodeTest with DummyTest {

  "Ladder" should {
    // this test gets dependencies through arguments
    "submit & get" in {
      (rnd: Rnd[IO], ladder: Ladder[IO]) =>
        for {
          user  <- rnd[UserId]
          score <- rnd[Score]
          _     <- ladder.submitScore(user, score)
          res   <- ladder.getScores.map(_.find(_._1 == user).map(_._2))
          _     = assert(res contains score)
        } yield ()
    }

    // other tests get dependencies via ZIO Env:
    "return higher score higher in the list" in {
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

        _ = if (score1 > score2) {
          assert(user1Rank < user2Rank)
        } else if (score2 > score1) {
          assert(user2Rank < user1Rank)
        }
      } yield ()
    }
  }

}
``` 

### Docker Test Resources

There is some documentation in the [release notes for 0.9.13](https://github.com/7mind/izumi/releases/tag/v0.9.13)
and there's an [example PR](https://github.com/7mind/distage-livecode/pull/2/files) showing how to use them. 

Further documentation TBD: [Issue #761](https://github.com/7mind/izumi/issues/761)

### Integration Checks

Implementation classes that inherit from `izumi.distage.roles.model.IntegrationCheck` can specify a `resourceCheck()` method
that will be called before test instantiation to check if **external test dependencies** (such as docker containers above)
are available for the test or role. If not, the test will be canceled/ignored.

This feature allows you to e.g. selectively run only the fast in-memory tests that have no external dependencies if you have 
shut down your test environment.
