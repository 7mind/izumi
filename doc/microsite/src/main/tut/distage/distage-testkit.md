# distage-testkit

@@toc { depth=2 }

### Quick Start

The `distage-testkit` simplifies pragmatic pure-FP testing. The `DistageSpecScalatest` are
[ScalaTest](https://www.scalatest.org/) base classes for effect types of `Identity`, `F[_]`,
`F[+_, +_]` and `F[-_, +_, +_]`. These provide an interface similar to ScalaTest's
[`WordSpec`](http://doc.scalatest.org/3.1.0/org/scalatest/wordspec/AnyWordSpec.html). However,
`distage-testkit` has additional capabilities: first class support for effect types as well as
dependency injection.

Usage of `distage-testkit` generally follows these steps:

1. Extend a base class corresponding to the effect type:
    - No effect type - @scaladoc[`DistageSpecScalatest[Identity]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
    - `F[_]` - @scaladoc[`DistageSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
    - `F[+_, +_]` - @scaladoc[`DistageBIOSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOSpecScalatest)
    - `F[-_, +_, +_]` - @scaladoc[`DistageBIOEnvSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest)
2. Override `def config: TestConfig` to customize the @scaladoc[`TestConfig`](izumi.distage.testkit.TestConfig)
3. Establish test case context using [`should`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/ShouldVerb.html),
   [`must`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/MustVerb.html),
   and [`can`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/CanVerb.html).
4. Introduce test cases using one of the `in` methods. Test cases can have a variety of forms. From
   pure functions returning an
   [assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html) to effectful
   functions with dependencies.
    - No effect type / `Identity` - @scaladoc[`in`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$LowPriorityIdentityOverloads)
    - @scaladoc[`in` for `F[_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper)
    - @scaladoc[`in` for `F[+_, +_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper2)
    - @scaladoc[`in` for `F[-_, +_, +_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper3)
    - Magnet for test cases dependent on injectables: @scaladoc[`ProviderMagnet`](izumi.distage.model.providers.ProviderMagnet)

### API Overview

To demonstrate the usage of `distage-testkit` we'll consider a hypothetical game scoring system. This
will have a model, logic and service which we'll then define test cases to verify.

The highest value tests to develop, [in our experience](https://blog.7mind.io/constructive-test-taxonomy.html),
are those that verify communication behavior of components. These are tests of blackbox interfaces,
with atomic or group isolation levels.

For demonstration, our application will use `ZIO[-R, +E, +A]`. Starting with a model and service
interface:

```scala
package app
```
```scala mdoc:to-string
import zio._
import zio.console.{Console, putStrLn}

case class Score(value: Int)

case class Config(starValue: Int, mangoValue: Int)

trait BonusService {
  def queryCurrentBonus: Task[Int]
}

object Score {
  val zero = Score(0)

  def addStar(config: Config, score: Score) =
    score.copy(value = score.value + config.starValue)

  def echoConfig(config: Config): RIO[Has[Console.Service], Config] =
    for {
      _ <- putStrLn(config.toString)
    } yield config

  def addMango(config: Config, score: Score): RIO[Has[Console.Service] with Has[BonusService], Score] =
    for {
      bonusService <- RIO.service[BonusService]
      currentBonus <- bonusService.queryCurrentBonus
    } yield {
      val value = score.value + config.mangoValue + currentBonus
      score.copy(value = value)
    }
}
```

#### `DistageSpecScalatest` Base Classes

There are test suite base classes for functor, bifunctor and trifunctor effect types. Typically, pick
one that matches the application's effect type:

- No effect type - @scaladoc[`DistageSpecScalatest[Identity]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
- `F[_]` - @scaladoc[`DistageSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
- `F[+_, +_]` - @scaladoc[`DistageBIOSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOSpecScalatest)
- `F[-_, +_, +_]` - @scaladoc[`DistageBIOEnvSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest)

For the example application, the tests use a `ZIO[-R, +E, +A]` effect type. From above, this means
using `DistageBIOEnvSpecScalatest` for the test suite base class.

The default config (`super.config`) has a `pluginConfig` that will scan the package the test is in
for modules. See the @ref:[`distage-extension-plugins`](./distage-framework.md#plugins) documentation
for more information. For this example, the module will be provided using `moduleOverrides`:

```scala
package app
```
```scala mdoc:to-string
import com.typesafe.config.ConfigFactory
import izumi.distage.config.AppConfigModule
import izumi.distage.effect.modules.ZIODIEffectModule
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest}

abstract class Test extends DistageBIOEnvSpecScalatest[ZIO] with AssertIO {
  val defaultConfig = Config(starValue = 10, mangoValue = 256)

  override def config = super
    .config.copy(
      moduleOverrides = new ModuleDef {
        include(AppConfigModule(ConfigFactory.defaultApplication))
        include(ZIODIEffectModule)
        make[Config].from(defaultConfig)
        make[Console.Service].fromHas(Console.live)
      }
    )
}
```


#### Test Cases

In `WordSpec`, a test case is a sentence (a `String`) followed by `in` then the body. In
`distage-testkit` the body of the test case is not limited to only a function returning an
[assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html).
@scaladoc[Functions that take arguments](izumi.distage.model.providers.ProviderMagnet)
and functions using effect types are also supported.  Functions arguments and effect environments
will be provided according to the `distage` object graph.

#### Test Cases - Assertions

All of the base classes support test cases that are: assertions ; functions returning an
assertion; functions returning unit that fail on exception.

These are introduced using `in` from
@scaladoc[DistageAbstractScalatestSpec.LowPriorityIdentityOverloads](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$LowPriorityIdentityOverloads)

The assertion methods are the same as ScalaTest: The base classes extend
[ScalaTest Assertions](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html)

```scala mdoc:invisible
// minimal check of that scalatest ref
import org.scalatest.Assertions
```

For the example application:

```scala
package app
```
```scala mdoc:to-string
final class ScoreSimpleTest extends Test {
  "Score" should {

    "increase by config star value" in {
      val starValue = util.Random.nextInt()
      val mangoValue = util.Random.nextInt()
      val config = Config(starValue, mangoValue)
      val expected = Score(starValue)
      val actual = Score.addStar(config, Score.zero)
      assert(actual == expected)
    }

    // Use Config is from the module in the `Test` class above
    "increase by config start value from DI" in {
      config: Config =>
        val expected = Score(defaultConfig.starValue)
        val actual = Score.addStar(config, Score.zero)
        assert(actual == expected)
    }
  }
}
```

```scala mdoc:invisible
// run the test to verify the docs
// Not enough value to showing the output. plus the formatting is bad.
// org.scalatest.shortstacks.nocolor.run(new ScoreSimpleTest)
```

#### Assertions with Effects

All of the base classes support test cases that are effects with assertions. Like above, functions
returning effects will have arguments provided from the DI graph. These test cases are supported by
@scaladoc[`in` from DSWordSpecStringWrapper](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper).

The different effect types fix the `F[_]` argument for this syntax:

- `DistageSpecScalatest`: `F[_]`
- `DistageBIOSpecScalatest`: `F[Throwable, _]`
- `DistageBIOEnvSpecScalatest`: `F[Any, Throwable, _]`

For the example application we'll use this to verify the `Score.echoConfig` method.  Additionally,
the `Config` required is from the `distage` object graph defined in `moduleOverrides` above. By using
is a function from `Config` the required argument will be injected by `distage-testkit`.

```scala
package app
```
```scala mdoc:to-string
final class ScoreEffectsTest extends Test {
  "testkit operations with effects" should {

    "assertions in effects" in {
      (config: Config) =>
        for {
          actual <- Score.echoConfig(config)
          _ <- assertIO(actual == config)
        } yield ()
    }

    "assertions from effects" in {
      (config: Config) =>
        Score.echoConfig(config) map {
          actual => assert(actual == config)
        }
    }
  }
}
```

```scala mdoc:invisible
//org.scalatest.shortstacks.nocolor.run(new ScoreEffectsTest)
```

#### Assertions with Effects with Environments

@scaladoc[The `in` method for `F[_, _, _]` effect types](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper3)
supports injection of environments from the object graph. This is in addition to the simple
assertions and assertions with effects described above.

A test that verifies the bonus service always returns one would be:

```scala
package app
```
```scala mdoc:to-string
abstract class BonusServiceIsZeroTest extends Test {
  "BonusService" should {
    "return one" in {
      for {
        bonusService <- ZIO.service[BonusService]
        currentBonus <- bonusService.queryCurrentBonus
        _ <- putStrLn(s"currentBonus = $currentBonus")
        _ <- assertIO(currentBonus == 1)
      } yield ()
    }
  }
}
```

While this compiles, this test cannot be run without the object graph containing a `BonusService`
resource.
For `ZIO[-R, +E, +A]` the `Has` bindings are injected from `ZLayer`, `ZManaged` `ZIO` or any
`F[_, _, _]: BIOLocal`.
See @ref[here for details on ZIO Has injection ](basics.md#zio-has-bindings).

Our example application has a dummy and production implementation of the `BonusService`.  For each
implementation a `ZManaged` is provided. With the `ZManaged` resources added to the object
graph test cases can inject `Has[BonusService]`.

For demonstration of [reuse and memoization](#resource-reuse-memoization) the bonus value will be
equal to the number of times the resource was acquired.

```scala
package app
```
```scala mdoc
object DummyBonusService {
  var acquireCount: Int = 0
  var releaseCount: Int = 0

  class Impl(bonusValue: Int) extends BonusService {
    override def queryCurrentBonus = UIO(bonusValue)
  }

  val acquire = Task {
    acquireCount += 1
    new Impl(acquireCount)
  }

  def release: UIO[Unit] = UIO {
    releaseCount += 1
  }

  val managed = acquire.toManaged(_ => release)
}
```

This small implementation is useful for verification. Both automated tests as well as functional
prototypes. For a real system we'd have a production implementation. Such as an implementation
that performs an HTTP requests to a REST service. We'll introduce a production service, but the
actual query will be unimplemented.

```scala
package app
```
```scala mdoc
object ProdBonusService {
  class Impl(console: Console.Service, url: String) extends BonusService {
    override def queryCurrentBonus = for {
      _ <- console.putStrLn(s"querying $url")
    } yield ???
  }

  val acquire = for {
    console <- ZIO.service[Console.Service]
    impl <- Task(new Impl(console, "https://my-bonus-server/current-bonus.json"))
  } yield impl

  def release: UIO[Unit] = UIO {
    ()
  }

  val managed = acquire.toManaged(_ => release)
}
```

#### Pattern: Dual Test Tactic

The testing of `BonusService` in the example application will follow the Dual Test Tactic. See
the blog post [Unit, Functional, Integration? You are doing it
wrong](https://blog.7mind.io/constructive-test-taxonomy.html) for a discussion of test taxonomy and
the value of this tactic.

A `ZIO` resouce for `BonusService` must be in the `distage` object graph for a `Has[BonusService]` to be
injected into the `ZIO` environment. One option: Define separate modules for the dummy and prod
implementations. One module would be referenced by tests and the other by production. However, this
is not as useful as both implementations in the same object graph but different activations.

The example application will use the
@scaladoc[StandardAxis.Repo](izumi.distage.model.definition.StandardAxis$$Repo$) `Dummy` and `Prod`
tags.

```scala
package app
```
```scala mdoc:to-string
import distage.plugins._
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo

object BonusServicePlugin extends PluginDef {
  make[BonusService]
    .fromHas(DummyBonusService.managed)
    .tagged(Repo.Dummy)

  make[BonusService]
    .fromHas(ProdBonusService.managed)
    .tagged(Repo.Prod)
}
```

Note that the `BonusServicePlugin` is not explicitly added to the `Test.config`:
This `PluginDef` is in the same package as the test. Namely `app`. By default,
the `pluginConfig` for the test will include the test's pacakge. Which will
be scanned by `distage` for `PluginDef` instances.

Continuing with the pattern, a trait will control which repo is activated:

```scala
package app
```
```scala mdoc
trait DummyTest extends Test {
  override def config = super
    .config.copy(
      activation = Activation(Repo -> Repo.Dummy)
    )
}

trait ProdTest extends Test {
  override def config = super
    .config.copy(
      activation = Activation(Repo -> Repo.Prod)
    )
}
```

With these a production test and a dummy test can be introduced for the example
application. Note how these are the same scenario, `BonusServiceIsZeroTest`,
but differ in activations.

When extended beyond this small example this pattern simplifies system level tests, sanity checks,
and even a pragmatic form of
[N-Version Programming](https://en.wikipedia.org/wiki/N-version_programming)

```scala
package app
```
```scala mdoc:to-string
final class ProdBonusServiceIsZeroTest extends BonusServiceIsZeroTest with ProdTest

final class DummyBonusServiceIsZeroTest extends BonusServiceIsZeroTest with DummyTest
```

<pre>
```scala mdoc:invisible
// change this block to `passthrough` instead of `invisible` to view test results.
// The goal is to demonstrate testkit plugin integration. `package` is not
// currently supported in mdoc code. To hack around this the `package app` code
// blocks are not interpreted and the actual test tested is the one below.
izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.resetRegistry()
final class MdocBonusServiceIsZeroTest extends BonusServiceIsZeroTest with DummyTest {
  override def config = super.config.copy(
    pluginConfig = super.config.pluginConfig.copy(
      overrides = Seq(BonusServicePlugin)
    )
  )
}
val t = new MdocBonusServiceIsZeroTest
org.scalatest.shortstacks.nocolor.run(t)
```
</pre>

#### Test Case Context

The `testkit` ScalaTest base classes include the verbs for establishing test context:

- [`should`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/ShouldVerb.html)
- [`must`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/MustVerb.html)
- [`can`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/CanVerb.html)

#### Configuration

The test suite class for your application should override the `def config: TestConfig` attributed.
Among other options, the config defines the plugin configuration, module overrides and activation
axes. See the d@scaladoc[`TestConfig` API docs](izumi.distage.testkit.TestConfig) for more
information.

### Syntax Summary

For `F[_]` including `Identity`:

- `in { assert(???) }` - test case is a function returning an
   [assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html).
- `in { (a: A, b: B) => assert(???) }` - test case is a function returning an assertion. The `a` and
   `b` will be injected from the object graph.
- `in { (a: A, b: B) => ???: F[Unit] }` - test case is a function returning an effect to be
  executed. The `a` and `b` will be injected from the object graph. The test case will fail if the
  effect fails.
- `in { (a: A, b: B) => ???: F[Assertion] }` - test case is a function returning an effect to be
  executed. The `a` and `b` will be injected from the object graph. The test case will fail if the
  effect fails or produces a failure assertion.

For `F[-_, +_, +_]`, same as above with `F[Any, Throwable, _]`:

- `in { ???: F[zio.Has[C] with zio.Has[D], Throwable, Unit] }` - test case is an effect requiring an
  environment. The test case will fail if the effect fails. The environment will be injected from
  the object graph.
- `in { ???: F[zio.Has[C] with zio.Has[D], Throwable, Assertion] }` - test case is an effect
  requiring an environment. The test case will fail if the effect fails or produces a failure
  assertion. The environment will be injected from the object graph.
- `in { (a: A, b: B) => ???: F[zio.Has[C] with zio.Has[D], Throwable, Assertion] }` - test case is a
  function producing an effect requiring an environment. All of `a: A`, `b: B`, `Has[C]` and `Has[D]`
  will be injected from the object graph.

Provided by trait @scaladoc[AssertIO](izumi.distage.testkit.scalatest.AssertIO):

- `assertIO(???: Boolean): zio.UIO[Assertion]`

Provided by trait @scaladoc[AssertCIO](izumi.distage.testkit.scalatest.AssertCIO):

- `assertIO(???: Boolean): cats.effect.IO[Assertion]`

Provided by trait @scaladoc[AssertBIO](izumi.distage.testkit.scalatest.AssertBIO):

- `assertBIO[F[+_, +_] : BIO](???: Boolean): F[Nothing, Assertion]`

### Resource Reuse - Memoization

Injected values are summoned from the dependency injection graph for each test. Without
using memoization resources will be acquired and released for each test. This may be
unwanted. For instance, a single Postgres Docker may be wanted for all tests.
The test config has a
@scaladoc[`memoizationRoots`](izumi.distage.testkit.TestConfig#memoizationRoots)
property for sharing components across tests.

### Test Selection

#### Using `IntegrationCheck`

Implementation classes that inherit from
@scaladoc[`izumi.distage.roles.model.IntegrationCheck`](izumi.distage.roles.model.IntegrationCheck) (
can specify a `resourceCheck()` method that will be called before test instantiation to check if
external test dependencies (such as docker containers in
@ref[distage-framework-docker](distage-framework-docker.md#docker-test-resources)) are available for
the test or role. If not, the test will be canceled/ignored.

This feature allows you to e.g. selectively run only the fast in-memory tests that have no external
dependencies if you have shut down your test environment.

Integration checks are executed only in `distage-testkit` tests and `distage-framework`'s
@ref[Roles](distage-framework.md#roles).

Use @scaladoc[StartupPlanExecutor](izumi.distage.roles.services.StartupPlanExecutor) to execute the
checks manually.

### Parallel Execution

TODO

### References

- Slides for [Hyper-pragmatic Pure FP testing with
  distage-testkit](https://www.slideshare.net/7mind/hyperpragmatic-pure-fp-testing-with-distagetestkit)
- Slides for [Scala, Functional Programming and Team Productivity
  ](https://www.slideshare.net/7mind/scala-functional-programming-and-team-productivity)
- [distage Example Project](https://github.com/7mind/distage-example) project shows how to use
  `distage-testkit`
- The [Hyper-pragmatic Pure FP Testing with
  distage-testkit](https://www.youtube.com/watch?v=CzpvjkUukAs) talk is an overview of the concepts,
  design and usage.
- 7mind's blog [Constructive Test Taxonomy](https://blog.7mind.io/constructive-test-taxonomy.html)
- [N-Version Programming](https://en.wikipedia.org/wiki/N-version_programming)

## Additional example code

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
    import zio.{IO, ZIO, URIO, Has}
    import repo.Ladder
    type LadderEnv = Has[Ladder[IO]]
    type RndEnv = Has[Rnd[IO]]
    object ladder extends Ladder[ZIO[LadderEnv, ?, ?]] {
      def submitScore(userId: UserId, score: Score): ZIO[LadderEnv, QueryFailure, Unit] = ZIO.accessM(_.get.submitScore(userId, score))
      def getScores: ZIO[LadderEnv, QueryFailure, List[(UserId, Score)]]                = ZIO.accessM(_.get.getScores)
    }
    object rnd extends Rnd[ZIO[RndEnv, ?, ?]] {
      override def apply[A]: URIO[RndEnv, A] = ZIO.accessM(_.get.apply[A])
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
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest}
import leaderboard.model.{Score, UserId}
import leaderboard.repo.{Ladder, Profiles}
import leaderboard.zioenv.{ladder, rnd}
import zio.{ZIO, IO}

abstract class LeaderboardTest extends DistageBIOEnvSpecScalatest[ZIO] with AssertIO {
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
      DIKey[Ladder[IO]],
      DIKey[Profiles[IO]],
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
        } else IO.unit
      } yield ()
    }

    // you can also mix arguments and env at the same time
    "assign a higher position in the list to a higher score 2" in {
      ladder: Ladder[IO] =>
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
            } else IO.unit
          } yield ()
    }
  }

}
```

### Troubleshooting
