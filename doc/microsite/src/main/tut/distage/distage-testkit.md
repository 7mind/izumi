# distage-testkit

@@toc { depth=2 }

### Quick Start

The `distage-testkit` simplifies pragmatic pure functional programming testing. `DistageSpecScalatest` are
[ScalaTest](https://www.scalatest.org/) base classes for the effect types of `Identity`, `F[_]`,
`F[+_, +_]` and `F[-_, +_, +_]`. They provide an interface similar to ScalaTest's
[`WordSpec`](http://doc.scalatest.org/3.1.0/org/scalatest/wordspec/AnyWordSpec.html), however
`distage-testkit` has additional capabilities such as first class support for effect types and
dependency injection.

Usage of `distage-testkit` generally follows these steps:

1. Extend a base class corresponding to the effect type:
    - No effect type - @scaladoc[`DistageSpecScalatest[Identity]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
    - `F[_]` - @scaladoc[`DistageSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
    - `F[+_, +_]` - @scaladoc[`DistageBIOSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOSpecScalatest)
    - `F[-_, +_, +_]` - @scaladoc[`DistageBIOEnvSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest)
2. Override `def config: TestConfig` to customize the @scaladoc[`TestConfig`](izumi.distage.testkit.TestConfig)
3. Establish test case contexts using [`should`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/ShouldVerb.html),
   [`must`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/MustVerb.html),
   and [`can`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/CanVerb.html).
4. Introduce test cases using one of the `in` methods. These test cases can have a variety of forms, from
   pure functions returning an
   [assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html), to effectful
   functions with dependencies:
    - No effect type / `Identity` - @scaladoc[`in`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$LowPriorityIdentityOverloads)
    - @scaladoc[`in` for `F[_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper)
    - @scaladoc[`in` for `F[+_, +_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper2)
    - @scaladoc[`in` for `F[-_, +_, +_]`](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper3)
    - Magnet for test cases dependent on injectables: @scaladoc[`Functoid`](izumi.distage.model.providers.Functoid)

### API Overview

The highest value tests to develop [in our experience](https://blog.7mind.io/constructive-test-taxonomy.html) are those that verify the communication behavior of components. These are tests of blackbox interfaces,
with atomic or group isolation levels.

To demonstrate usage of `distage-testkit` we'll consider a hypothetical game score system. This
system will have a model, logic, and service which we'll then define test cases to verify. Our application will use `ZIO[-R, +E, +A]`.

We'll start with the following model and service interface for the game score system:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

import zio._
import zio.console.{Console, putStrLn}

case class Score(value: Int)

case class Config(starValue: Int,
                  mangoValue: Int,
                  defaultBonus: Int)

trait BonusService {
  def queryCurrentBonus: Task[Int]
  def increaseCurrentBonus(delta: Int): Task[Int]
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

This represents a game score system where the player can collect Stars or Mangoes with differently configured and calculated point values.

#### `DistageSpecScalatest` Base Classes

There are test suite base classes for functor, bifunctor and trifunctor effect types. We will be choosing the
one that matches our application's effect type from the following:

- No effect type - @scaladoc[`DistageSpecScalatest[Identity]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
- `F[_]` - @scaladoc[`DistageSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageSpecScalatest)
- `F[+_, +_]` - @scaladoc[`DistageBIOSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOSpecScalatest)
- `F[-_, +_, +_]` - @scaladoc[`DistageBIOEnvSpecScalatest[F]`](izumi.distage.testkit.scalatest.DistageBIOEnvSpecScalatest)

For our demonstration application the tests use the `ZIO[-R, +E, +A]` effect type. This means we'll be
using `DistageBIOEnvSpecScalatest` for the test suite base class.

The default config (`super.config`) has `pluginConfig`, which will scan the package the test is in
for according modules. See the @ref:[`distage-extension-plugins`](./distage-framework.md#plugins) documentation
for more information. For our demonstration the module will be provided using `moduleOverrides` like so:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

import com.typesafe.config.ConfigFactory
import distage.config.AppConfigModule
import distage.ModuleDef
import izumi.distage.effect.modules.ZIODIEffectModule
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest}

abstract class Test extends DistageBIOEnvSpecScalatest[ZIO] with AssertIO {
  val defaultConfig = Config(starValue = 10,
                             mangoValue = 256,
                             defaultBonus = 10)

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
`distage-testkit` the body of the test case is not limited to a function returning an
[assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html).
@scaladoc[Functions that take arguments](izumi.distage.model.providers.Functoid)
and functions using effect types are also supported.  Function arguments and effect environments
will be provided according to the `distage` object graph.

#### Test Cases - Assertions

All of the base classes support test cases that are:
  - Assertions.
  - Functions returning an assertion.
  - Functions returning unit that fail on exception.

These are introduced using `in` from
@scaladoc[DistageAbstractScalatestSpec.LowPriorityIdentityOverloads](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$LowPriorityIdentityOverloads)

The assertion methods are the same as ScalaTest as the base classes extend
[ScalaTest Assertions](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html).

```scala mdoc:invisible
// minimal check of that scalatest ref
import org.scalatest.Assertions
```

Let's now create a simple test for our demonstration application:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

final class ScoreSimpleTest extends Test {
  "Score" should {

    "increase by config star value" in {
      val starValue = util.Random.nextInt()
      val mangoValue = util.Random.nextInt()
      val defaultBonus = util.Random.nextInt()
      val config = Config(starValue, mangoValue, defaultBonus)
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

All of the base classes support test cases that are effects with assertions. As mentioned earlier, functions
returning effects will have arguments provided from the `distage` object graph. These test cases are supported by
@scaladoc[`in` from DSWordSpecStringWrapper](izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec$$DSWordSpecStringWrapper).

The different effect types fix the `F[_]` argument for this syntax:

- `DistageSpecScalatest`: `F[_]`
- `DistageBIOSpecScalatest`: `F[Throwable, _]`
- `DistageBIOEnvSpecScalatest`: `F[Any, Throwable, _]`

With our demonstration application we'll use this to verify the `Score.echoConfig` method.
The `Config` required is from the `distage` object graph defined in `moduleOverrides`. By using
a function from `Config`, the required argument will be injected by `distage-testkit`.

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

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
supports injection of environments from the object graph in addition to simple
assertions and assertions with effects.

A test that verifies the `BonusService` in our demonstration would be:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

abstract class BonusServiceTest extends Test {
  "BonusService" should {
    "initially use default bonus as current" in {
      for {
        bonusService <- ZIO.service[BonusService]
        currentBonus <- bonusService.queryCurrentBonus
        _ <- putStrLn(s"currentBonus = $currentBonus")
        _ <- assertIO(currentBonus == defaultConfig.defaultBonus)
      } yield ()
    }

    "increment by delta" in {
      val delta = util.Random.nextInt()
      for {
        bonusService <- ZIO.service[BonusService]
        initialBonus <- bonusService.queryCurrentBonus
        actualBonus <- bonusService.increaseCurrentBonus(delta)
        expectedBonus = initialBonus + delta
        _ <- assertIO(actualBonus == expectedBonus)
      } yield ()
    }
  }
}
```

While this compiles this test cannot be run without the object graph containing a `BonusService`
resource.
For `ZIO[-R, +E, +A]`, the `Has` bindings are injected from `ZLayer`, `ZManaged` `ZIO` or any
`F[_, _, _]: BIOLocal`.
See @ref[here for details on ZIO Has injection ](basics.md#zio-has-bindings).

Our demonstration application has a dummy and production implementation of the `BonusService`.  For each
implementation, a `ZManaged` is provided. With the `ZManaged` resources added to the object
graph test cases can inject `Has[BonusService]`.

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

object DummyBonusService {
  class Impl(var bonusValue: Int) extends BonusService {
    override def queryCurrentBonus = UIO(bonusValue)
    override def increaseCurrentBonus(delta: Int) = UIO {
        bonusValue += delta
        bonusValue
    }
  }

  val acquire = Task {
    new Impl(10)
  }

  def release: UIO[Unit] = UIO.unit

  val managed = acquire.toManaged(_ => release)
}
```

This small implementation is useful for verification in both automated tests as well as functional
prototypes.

For a real system we might build a production implementation like the following. This hypothetical
implementation would perform an HTTP request to a REST service. We'll introduce a production service,
but this actual query will be unimplemented for our demonstration:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

object ProdBonusService {
  class Impl(console: Console.Service, url: String) extends BonusService {
    override def queryCurrentBonus = for {
      _ <- console.putStrLn(s"querying $url")
    } yield ???
    override def increaseCurrentBonus(delta: Int) = for {
      _ <- console.putStrLn(s"post to $url")
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

The testing of `BonusService` in our demonstration application will follow the Dual Test Tactic. See
our blog post [Unit, Functional, Integration? You are doing it
wrong](https://blog.7mind.io/constructive-test-taxonomy.html) for a discussion of test taxonomy and
the value of this tactic.

A `ZIO` resource for `BonusService` must be in the `distage` object graph for a `Has[BonusService]` to be
injected into the `ZIO` environment. One option is to define separate modules for the dummy and production
implementations. One module would be referenced by tests and the other only by production. However, this
is not as useful as both implementations in the same object graph but different activations.

Our demonstration application will use the
@scaladoc[StandardAxis.Repo](izumi.distage.model.definition.StandardAxis$$Repo$) `Dummy` and `Prod`
tags:

```scala mdoc:invisible
implicit def _hack_whyDoesItNotWorkInMdocHuh_forcedRecompilationToken: izumi.distage.plugins.ForcedRecompilationToken["abc"] = null
```

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

import distage.plugins.PluginDef
import distage.Activation
import distage.StandardAxis.Repo

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
This `PluginDef` is in the same package as the test, namely `app`. By default
the `pluginConfig` for the test will include the test's package, which will
be scanned by `distage` for `PluginDef` instances.

Continuing with the pattern, a trait will control which repo is activated:

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

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

With these a production test and a dummy test can be introduced for the demonstration game score
application. Note how these are the same scenario, `BonusServiceTest`,
but differ in activations.

When extended beyond this small example, this pattern simplifies system level tests, sanity checks,
and even a pragmatic form of
[N-Version Programming](https://en.wikipedia.org/wiki/N-version_programming):

```scala mdoc:fakepackage:to-string
"fakepackage app": Unit

final class ProdBonusServiceTest extends BonusServiceTest with ProdTest

final class DummyBonusServiceTest extends BonusServiceTest with DummyTest
```

<pre>
```scala mdoc:invisible
// change this block to `passthrough` instead of `invisible` to view test results.
// The goal is to demonstrate testkit plugin integration. `package` is not
// currently supported in mdoc code. To hack around this the `package app` code
// blocks are not interpreted and the actual test tested is the one below.
izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.resetRegistry()
trait MdocTest extends DummyTest {
  override def config = super.config.copy(
    pluginConfig = super.config.pluginConfig.copy(
      overrides = Seq(BonusServicePlugin)
    )
  )
}

val mdocBonusServiceTest = new BonusServiceTest with MdocTest
org.scalatest.shortstacks.nocolor.run(mdocBonusServiceTest)
```
</pre>

#### Test Case Context

The `testkit` ScalaTest base classes include the following verbs for establishing test context:

- [`should`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/ShouldVerb.html)
- [`must`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/MustVerb.html)
- [`can`](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/verbs/CanVerb.html)

#### Configuration

The test suite class for your application should override the `def config: TestConfig` attributed.
The config defines the plugin configuration, memoization, module overrides and other options.

See also:

- @scaladoc[`TestConfig` API docs](izumi.distage.testkit.TestConfig).
- [Memoization](#resource-reuse---memoization)
- [Parallel Execution](#parallel-execution)

### Syntax Summary

For `F[_]` including `Identity`:

- `in { assert(???) }`: The test case is a function returning an
   [assertion](https://www.scalatest.org/scaladoc/3.2.0/org/scalatest/Assertions.html).
- `in { (a: A, b: B) => assert(???) }`: The test case is a function returning an assertion. The `a` and
   `b` will be injected from the object graph.
- `in { (a: A, b: B) => ???: F[Unit] }`: The test case is a function returning an effect to be
  executed. The `a` and `b` will be injected from the object graph. The test case will fail if the
  effect fails.
- `in { (a: A, b: B) => ???: F[Assertion] }`: The test case is a function returning an effect to be
  executed. The `a` and `b` will be injected from the object graph. The test case will fail if the
  effect fails or produces a failure assertion.

For `F[-_, +_, +_]`, it's same with `F[Any, _, _]`:

- `in { ???: F[zio.Has[C] with zio.Has[D], _, Unit] }`: The test case is an effect requiring an
  environment. The test case will fail if the effect fails. The environment will be injected from
  the object graph.
- `in { ???: F[zio.Has[C] with zio.Has[D], _, Assertion] }`: The test case is an effect
  requiring an environment. The test case will fail if the effect fails or produces a failure
  assertion. The environment will be injected from the object graph.
- `in { (a: A, b: B) => ???: F[zio.Has[C] with zio.Has[D], _, Assertion] }`: The test case is a
  function producing an effect requiring an environment. All of `a: A`, `b: B`, `Has[C]` and `Has[D]`
  will be injected from the object graph.

Provided by trait @scaladoc[AssertIO](izumi.distage.testkit.scalatest.AssertIO):

- `assertIO(???: Boolean): zio.UIO[Assertion]`

Provided by trait @scaladoc[AssertCIO](izumi.distage.testkit.scalatest.AssertCIO):

- `assertIO(???: Boolean): cats.effect.IO[Assertion]`

Provided by trait @scaladoc[AssertBIO](izumi.distage.testkit.scalatest.AssertBIO):

- `assertBIO[F[+_, +_] : BIO](???: Boolean): F[Nothing, Assertion]`

### Resource Reuse - Memoization

Injected values are summoned from the object graph for each test. Without using memoization, the
components will be acquired and released for each test. This may be unwanted. For example, a single
Postgres container may be required for a sequence of test cases.  In which case we'd want to memoize
the postgress component for the duration of those test cases.  Configuring memoization enables
changing whether instantiating a component results in a fresh component or reuses the existing,
memoized, instance.

Further, memoization is essential in scheduling parallel execution of tests. See [the parallel
execution section for further information.](#parallel-execution)

#### Memoization Environments

The memoization applied when an component is summoned is defined by the *memoization environment*.
Each distinct memoization environment uses a distinct memoization store. When a component instance is
memoized that instance is shared across all tests that use the same memoization environment.  The
@scaladoc[`TestConfig`](izumi.distage.testkit.TestConfig) contains the options that define the
memoization environment:

1. `memoizationRoots` - These components will be acquired once and shared across all tests that used
   the same memoization environment.
2. `activation` - Chosen activation axis. Changes in Activation that alter implementations of
   components in memoizationRoots OR their dependencies will cause the test to execute in a new
   memoization environment.
3. `pluginConfig` - Defines the plugins to source module definitions.
4. `forcedRoots` - Components treated as a dependency of every test. A component added to this and
   `memoizationRoots` will be acquired at the start of all tests and released at the end of all tests.
5. `moduleOverrides` - Overrides the modules from `pluginConfig`.

The module environment depends on instantiation of the `memoizationRoots` and `forcedRoots` components.
Changes to the config that alter implementations of these components *or* their dependencies will
change the memoization environment used. This includes, but is not limited to, changes to
`activation`, `pluginConfig` and `moduleOverrides`.

When the `TestConfig` option @scaladoc[`debugOutput`](izumi.distage.testkit.TestConfig)
is true the debug output will include memoization environment diagnostics.  This can also be
controlled using the `izumi.distage.testkit.debug` system property.

#### Examples

The first example will acquire the `BonusService` for each test case. This will not use memoization.

```scala mdoc:fakepackage
"fakepackage app": Unit

import izumi.distage.testkit.TestConfig

class NotUsingMemoTest extends DummyTest {
  override def config = super
    .config.copy(
      // require the test cases to run sequentially
      parallelTests = TestConfig.ParallelLevel.Sequential
    )

  "Not memoizing BonusService" should {
    "should use a new instance in the first case" in {
      val delta = util.Random.nextInt()

      for {
        bonusService <- ZIO.service[BonusService]
        // change the bonus service state
        currentBonus <- bonusService.increaseCurrentBonus(delta)
        expectedBonus = defaultConfig.defaultBonus + delta
        _ <- assertIO(currentBonus == expectedBonus)
      } yield ()
    }

    "and use a new instance in the second case" in {
      for {
        bonusService <- ZIO.service[BonusService]
        currentBonus <- bonusService.queryCurrentBonus
        // verify the state is unchanged from default
        _ <- assertIO(currentBonus == defaultConfig.defaultBonus)
      } yield ()
    }
  }
}
```

These two tests will run sequentially. There is no memoization configured for the dependencies. Each
test case will acquire a fresh instance from the object graph. For our demonstration this results in
a new `BonusService` instance for each test case.

<pre>
```scala mdoc:invisible
// change this block to `passthrough` instead of `invisible` to view test results.
izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.resetRegistry()
val mdocNotUsingMemoTest = new NotUsingMemoTest with MdocTest
org.scalatest.shortstacks.nocolor.run(mdocNotUsingMemoTest)
```
</pre>

Configuring the test to memoize `BonusService` will result in the same instance being used for both
test cases:

```scala mdoc:fakepackage
"fakepackage app": Unit

import distage.DIKey

class UsingMemoTest extends DummyTest {
  override def config = super
    .config.copy(
      memoizationRoots = super.config.memoizationRoots ++ Set(DIKey[BonusService]),
      // require the test cases to run sequentially
      parallelTests = TestConfig.ParallelLevel.Sequential
    )

  val delta = util.Random.nextInt()

  "Memoizing BonusService" should {
    "should use a new instance in the first case" in {
      for {
        bonusService <- ZIO.service[BonusService]
        _ <- console.putStrLn(s"bonusService = ${bonusService}")
        // change the bonus service state
        currentBonus <- bonusService.increaseCurrentBonus(delta)
        expectedBonus = defaultConfig.defaultBonus + delta
        _ <- assertIO(currentBonus == expectedBonus)
      } yield ()
    }

    "and use the same instance in the second case" in {
      for {
        bonusService <- ZIO.service[BonusService]
        _ <- console.putStrLn(s"bonusService = ${bonusService}")
        currentBonus <- bonusService.queryCurrentBonus
        expectedBonus = defaultConfig.defaultBonus + delta
        _ <- assertIO(currentBonus == expectedBonus)
      } yield ()
    }
  }
}
```

Note that the test did *not* use the same `BonusService` instance as `NotUsingMemoTest`. The config
for each test has different memoization roots. This results in different [memoization
environments](#memoization-environments).

If the memoization environments are equal then the components will be shared. For our example, any
other test with the same memoization environment would use the same `BonusService` instance.

```scala mdoc:fakepackage
"fakepackage app": Unit

class AnotherUsingMemoTest extends DummyTest {
  // This is the same memoization environment even tho the config is declared separately
  override def config = super
    .config.copy(
      memoizationRoots = super.config.memoizationRoots ++ Set(DIKey[BonusService]),
      // require the test cases to run sequentially
      parallelTests = TestConfig.ParallelLevel.Sequential
    )

  "Another test using BonusService" should {
    "use the same instance" in {
      for {
        bonusService <- ZIO.service[BonusService]
        _ <- console.putStrLn(s"bonusService = ${bonusService}")
        currentBonus <- bonusService.queryCurrentBonus
        _ <- console.putStrLn(s"currentBonus = ${currentBonus}")
      } yield ()
    }
  }
}
```

Both tests, all three test cases, will use same memoization environment and `bonusService` instance:

```
<from logging>
[info] phase=late, memoEnv=407164314 Memoization environment with suites=1 tests=2 test_suites=
<from console>
bonusService = repl.MdocSession$App$DummyBonusService$Impl@2843b83b
bonusService = repl.MdocSession$App$DummyBonusService$Impl@2843b83b
bonusService = repl.MdocSession$App$DummyBonusService$Impl@2843b83b
```

<pre>
```scala mdoc:invisible
// change this block to `passthrough` instead of `invisible` to view test results.
izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton.resetRegistry()
val mdocUsingMemoTest = new UsingMemoTest with MdocTest
val mdocAnotherUsingMemoTest = new AnotherUsingMemoTest with MdocTest
// while this looks to only run one test this will run both.
org.scalatest.shortstacks.nocolor.run(mdocUsingMemoTest)
// this only outputs the trace of the second tests run
org.scalatest.shortstacks.nocolor.run(mdocAnotherUsingMemoTest)
```
</pre>

#### Psuedocode

Suppose that the lookup of an instance for a component uses a hypothetical function `lookup(graph,
type and tag)`. This function is memoized using storage specific to the current memoization
environment. This memoization environment is uniquely defined by the test config options above. This
would have pseudocode like:

```
rootComponents = planRoots(memoizationRoots, activation, forcedRoots, ...)
memoizationEnvironment = getOrCreate(rootComponents)
memoizationStore = memoizationEnvironment.store
...
for each test case
  add forcedRoots to component dependencies
  for each component dependency:
    if memoizationStore contains component
    then
      instance = memoizationStore.lookup(component)
    else
      instance = acquireComponent(component)
      if (component is in memoizationRoot paths)
        memoizationStore.add(component, instance)
    ...
```

### Forced Roots

The `forcedRoots` of `TestConfig` specifies components added to the dependencies of every test within
this memoization environment. Without memoization these will be acquired and release each test
case. With memoization these will be acquired before all and released after all tests within this
memoization environment.



### Test Selection

#### Using `IntegrationCheck`

Implementation classes that inherit from
@scaladoc[`izumi.distage.framework.model.IntegrationCheck`](izumi.distage.framework.model.IntegrationCheck). You
can specify a `resourceCheck()` method that will be called before test instantiation to check if
external test dependencies (such as Docker containers in
@ref[distage-framework-docker](distage-framework-docker.md#docker-test-resources)) are available for
the test or role. If not, the test will be canceled/ignored.

This feature allows you to therefore selectively run only the fast in-memory tests that have no external
dependencies. Integration checks are executed only in `distage-testkit` tests and `distage-framework`'s
@ref[Roles](distage-framework.md#roles).

Use @scaladoc[StartupPlanExecutor](izumi.distage.roles.launcher.services.StartupPlanExecutor) to execute the
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

## Extended Example

This is an excerpt from [distage-example](https://github.com/7mind/distage-example). Techniques in that example to look for:

- Placing the `Profiles` component in the `memoizationRoots`. The axis `Repo.Prod` uses a postgres
  docker container. This is shared across test cases since the `Profiles[IO]` depends on the
  postgres connection which then depends on the container instance.
- Use of `Scene.Managed` to use `Axis.Prod` components in a managed environment.

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
import distage.{Activation, DIKey, ModuleDef}
import distage.StandardAxis.Repo
import distage.plugins.PluginConfig
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
