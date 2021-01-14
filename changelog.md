`1.0.0`

<p align="center">
  <a href="https://izumi.7mind.io/">
  <img width="40%" src="https://github.com/7mind/izumi/blob/develop/doc/microsite/src/main/tut/media/izumi-logo-full-purple.png?raw=true" alt="Izumi"/>
  </a>
</p>

<details>
  <summary>What is it?</summary>


Izumi (*jp. 泉水, spring*) is an ecosystem of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.

including the following components:

1. [distage](https://izumi.7mind.io/distage/) – Transparent and debuggable Dependency Injection framework for pure FP Scala,
2. [distage-testkit](https://izumi.7mind.io/distage/distage-testkit) – Hyper-pragmatic pure FP Test framework. Shares heavy resources globally across all test suites; lets you easily swap implementations of component; uses your effect type for parallelism.
3. [distage-framework-docker](https://izumi.7mind.io/distage/distage-framework-docker) – A distage extension for using docker containers in tests or for local application runs, comes with example Postgres, Cassandra, Kafka & DynamoDB containers.
4. [LogStage](https://izumi.7mind.io/logstage/) – Automatic structural logs from Scala string interpolations,
5. [BIO](https://izumi.7mind.io/bio/) - A typeclass hierarchy for tagless final style with Bifunctor and Trifunctor effect types. Focused on ergonomics and ease of use with zero boilerplate.
6. [izumi-reflect](https://github.com/zio/izumi-reflect) (moved to [zio/izumi-reflect](https://github.com/zio/izumi-reflect)) - Portable, lightweight and kind-polymorphic alternative to `scala-reflect`'s Typetag for Scala, Scala.js, Scala Native and ([soon](https://github.com/7mind/dotty-typetag-research)) Dotty
7. [IdeaLingua](https://izumi.7mind.io/idealingua/) (moved to [7mind/idealingua-v1](https://github.com/7mind/idealingua-v1)) – API Definition, Data Modeling and RPC language, optimized for fast prototyping – like gRPC or Swagger, but with a human face. Generates RPC servers and clients for Go, TypeScript, C# and Scala,
8. [Opinionated SBT plugins](https://izumi.7mind.io/sbt/) (moved to [7mind/sbtgen](https://github.com/7mind/sbtgen)) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven)
9. [Percept-Plan-Execute-Repeat (PPER)](https://izumi.7mind.io/pper/) – A pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.

</details>

---------------------------------------

We're happy to present Izumi 1.0 release! There are a lot of features included this release that we have not anticipated that we'd be able to include in 1.0,
and even some that we did not consider possible to actually implement in Scala. It happened to be quite delayed because of that, but we believe that in the end it was worth the wait.

Compatibilty note: there are multiple renames for all kinds of entities in this release, old names are preserved as deprecated aliases to ease migration from 1.0.

Thanks to all our contributors and especially @Caparow, @CoreyOConnor and @VladPodilnyk!

# Major Features since 0.10.18

## distage

### Compile-time safety

Total compile-time safety lands for `distage` in 1.0, although previously we thought it was impossible to implement!

The new compile-time checks provide fast feedback during development and remove the need to launch tests or launch application itself or write special tests to check the correctness of wiring.

For users of `distage-extension-config`, config parsing will be verified at compile-time against the default config files in resource folder.
There is an option to disable config checking or check against a specific resource file instead of defaults.

Checking is not enabled out-of-the-box, you must enable it by adding a “trigger” object in test scope. This object will emit compile-time errors for any issues or omissions in your `ModuleDefs`. It will recompile itself as necessary to provide feedback during development.

See [“Compile-time checks” chapter](https://izumi.7mind.io/distage/distage-framework#compile-time-checks) for details.

### Mutators

Since 1.0 you may add "mutator" bindings to your module definitions. Mutators apply functions to an object before it's visible to others in the object graph, they may also request additional dependencies for use in the function. Mutators provide a way to do partial overrides or slight modifications of some existing component without redefining it fully.

Motivating example: Suppose you're using a config case class in your `distage-testkit` tests, and for one of the test you want to use a modified value for one of the fields in it. Before 1.0 you'd have to duplicate the config binding into a new key and apply the modifying function to it:

```scala
import distage.{Id, ModuleDef}
import distage.config.ConfigModuleDef
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.SpecIdentity

class MyTest extends SpecIdentity {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ConfigModuleDef {
      makeConfig[Config]("config.myconfig").named("duplicate")
      make[Config].from {
        (thatConfig: Config @Id("duplicate")) =>
          modifyingFunction(thatConfig)
      }
    }
  )
}
```

Now instead of overriding the entire binding, we may use a mutator:

```scala
class MyTest extends SpecIdentity {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      modify[Config](modifyingFunction(_))
    }
  )
}
```

See [“Mutator Bindings” chapter](https://izumi.7mind.io/distage/basics#mutator-bindings) for details.

### Out-of-the-box typeclass instances

Before 1.0, it was a major chore to have to add bindings for typeclass instances such as `Sync[F]` and `ContextShift[F]` for your effect type to use them in DI. While pre-made modules existed in `distage-framework` library, they were hard to discover and had to be manually included into your own modules.

Since 1.0, instances for [BIO](https://izumi.7mind.io/bio/) & [cats-effect](https://typelevel.org/cats-effect/) typeclass hierarchies are added automatically when you specify the effect type for your wiring and are can be summoned in the object graph without adding them manually, out of the box.

See [“Out-of-the-box typeclass instances” chapter](https://izumi.7mind.io/distage/basics#out-of-the-box-typeclass-instances) for details.

## distage-testkit

### Multi-Level Memoization

`distage-testkit`'s memoization mechanism allows you to share heavy components, such as docker containers managed by [`distage-framework-docker`](https://izumi.7mind.io/distage/distage-framework-docker), globally across all test suites, or across a defined subset of test suites. The scope of sharing is derived implicitly from parameters set in test suite's `TestConfig`, test suites with compatible `TestConfig`'s — compatible in a way that executing them will result in identical memoized components — will acquire those memoized components only once and share them.

This scheme had a weakness in that incompatible `TestConfig`s would cause a re-creation of all memoized instances, even very heavy ones.

Since 1.0, memoization has been generalized to support unlimited nesting, with new strategy, the memoization environment may be manually partitioned into levels and if a change in `TestConfig` does not cause a divergence at one of the levels, the nested levels may then fully reuse the object sub-graph of all parent levels that do not diverge.

For clarity, the memoization tree structure is printed before test runs. For example, a memoization tree of a project with the following test suites:

```scala
class SameLevel_1_WithActivationsOverride extends Spec3[ZIO] {
  override protected def config: TestConfig = {
    super.config.copy(
        memoizationRoots = Map(
          1 -> Set(DIKey[MemoizedInstance], DIKey[MemoizedLevel1]),
          2 -> Set(DIKey[MemoizedLevel2]),
        ),
    )
  }
}

class SameLevel_1_2_WithAdditionalLevel3 extends SameLevel_1_WithActivationsOverride {
  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots =
        super.config.memoizationRoots ++
        Set(DIKey[MemoizedLevel3]),
    )
  }
}
```

May be visualized as follows:

![Memoization Tree Log during tests](https://izumi.7mind.io/distage/media/memoization-tree.png)

See ["Memoization Levels" chapter](https://izumi.7mind.io/distage/distage-testkit#multi-level-memoization) for details.

### distage-testkit documentation

@CoreyOConnor has contributed a brand new, shining microsite page for `distage-testkit`! It contains both reference material and a tutorial to get started, so check it out on ["distage-testkit" section](https://izumi.7mind.io/distage/distage-testkit)!

## distage-framework-docker

You can now set `Docker.ContainerConfig` `alwaysPull` field to `false` to disable pulling docker images for the container by @vilunov (#1300)

## BIO

`BIO` is an hierarchy of typeclasses for tagless final style with bifunctor and trifunctor effects. In 1.0 it has been majorly restructured.

First, all classes have been renamed according to a new naming convention:

* Bifunctor class naming pattern has changed from `BIO<name>` to `<name>2`. e.g. `BIOFunctor` is now `Functor2`, `BIO` class is now `IO2`
* Trifunctor class naming pattern has changed from `BIO<name>3` to `<name>3`, e.g. `BIOMonadAsk3` is now `MonadAsk3`, `BIO3` is now `IO3`

When using old names, deprecation warnings will guide towards the new names.

Second, `Parallel*`, `Concurrent*` and `Temporal*` capabilities now do not require `IO*` (an analogue of `cats-effect`'s Sync), enabling you to use concurrency and parallelism without inviting side-effects in.

Third, compatibility with `cats-effect` has been improved. @VladPodilnyk has contributed Discipline laws (#1249) which allowed extending cats conversions' support to cover `ConcurrentEffect` whereas before BIO could only convert up to `Concurrent`. Now you can run `http4s` and all the other cats-effect libraries with just BIO typeclasses, without requiring any cats-effect typeclasses.

Forth, new primitives were added, bifunctor `Free` monad, as well as `FreeError` and `FreePanic` data types were contributed by @Caparow, they provide building blocks for DSLs when combined with a DSL describing functor. `Morphism1/2/3` provide unboxed natural transformations for functors with 1,2,3-arity respectively, with `Isomorphism1/2/3` modeling two-way transformations.

And last, instances of BIO for `monix-bio` have been added to default implicit scope, also contributed by @VladPodilnyk. (these do not force a `monix-bio` dependency)

The new hierarchy is visualized as follows:

![BIO Hierarchy](https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg)

See ["BIO Hierarchy" chapter](https://izumi.7mind.io/bio/) for details.

## LogStage

@an-tex has contributed a fix for `LogstageCodec[Throwable]` instance (#1206)

Classes were renamed to follow the new `BIO` naming convention, e.g. `LogBIO` has been renamed to `LogIO2`. When using old names, deprecation warnings will guide towards the new names.

## Other changes

### Reworked Role Launcher

`distage` roles are a generalization of application entrypoints. An application may have many roles, which can be either one-shot tasks or persistent services.

Roles come with default command-line parsing and config reading behavior. Before `1.0`, these and other aspects of `distage-framework`'s Role Launcher have been hard to customize or override. Since 1.0 Role Launcher has been rewritten to be configured using `distage` itself and all of its behavior can now be customized or
overridden using custom modules.

See ["Roles" chapter](https://izumi.7mind.io/distage/distage-framework.html#roles) for details.

### Compile-time plugin discovery

`distage` supports classpath discovery of modules through an extension. Before 1.0, this extension was hard to use in Graal Native Image applications due to reliance on runtime reflection in the implementation.

Since 1.0 you can perform plugin scanning at compile-time using [`PluginConfig.compileTime`](https://izumi.7mind.io/api/izumi/distage/plugins/PluginConfig$.html#compileTime(pluginsPackage:String):izumi.distage.plugins.PluginConfig).

Compile-time scanning enables plugin-based development workflow on platforms without runtime reflection, such as Graal Native, however the runtime extension aspect of plugins is lost.

See ["Compile-time scanning" chapter](https://izumi.7mind.io/distage/distage-framework#compile-time-scanning) for details.

### IntegrationCheck[F]

Contributed by @Caparow, `IntegrationCheck`'s may now be parameterized by `F[_]` and use effects.

See ["Using IntegrationCheck" chapter](https://izumi.7mind.io/distage/distage-testkit#using-integrationcheck) for details.

### Lifecycle instances

Contributed by @VladPodilnyk, `distage.Lifecycle` now has typeclass instances for `cats` `Monad`, `Monoid` and `BIO` `Functor2`, `Functor3` in default implicit scope. (these do not force a `cats` dependency)

#### Changes in Activation behavior

Configuring the object graph using `Activation` changed in 1.0, namely:

1. Opposite activations now prune. Before, a sole binding with an activation such as `make[MyRepo].tagged(Repo.Dummy)` would be accessible in the object graph even if a contradictory activation such as `Repo.Prod` was activated. This is fixed now, and `MyRepo` is removed in such a case, it can only be made available again in an opposite axis by defining a binding with `.tagged(Repo.Prod)`

2. Set elements and mutators are now subject to configuration. Before, a declarations such as
  ```scala
  many[Int]
    .add(1).tagged(Mode.Test)
    .add(3).tagged(Mode.Prod)
  ```
  would not have the intended effect, with _both_ integers remaining in `Set[Int]`. Since 1.0, activations apply to set elements too, so `Set[Int]` would contain either `Set(1)` or `Set(3)` depending on the value of `Mode`.
  For mutators, individual mutations can be tagged, and will be applied according to activation:
  ```scala
  modify[Int](_ + 10).tagged(Mode.Test)
  modify[Int](_ + 1).tagged(Mode.Prod)
  ```

3. Semantics for multi-dimensional activations have been cleaned up, when used, they now behave according to "specificity rules". Bindings with no assigned activations can now behave as "defaults" in cases where all other bindings are definitely unapplicable. See ["Specificity and defaults" chapter](https://izumi.7mind.io/distage/basics#specificity-and-defaults) for details

See ["Activation Axis" chapter](https://izumi.7mind.io/distage/basics#activation-axis) for details

#### Renames

Multiple entities in `distage` have been renamed, including the following:

* `DIResource`, `DIResourceBase` -> `Lifecycle`
* `DIEffect` -> `QuasiIO`
* `ProviderMagnet` -> `Functoid`

When using old names, deprecation warnings will guide towards the new names.

# Pull Requests merged since 0.10.18

  * Fix #1226 Finalize static plan checker API (#1296)
  * Add a docker container config flag to control explicit image pulling (#1300)
  * Fix #1226 New compile-time checker API (#1285)
  * Remove 2.12 compat from FunctionK/FunctionKK (you need `-Xsource:2.13` for the rest of the library anyway and it enables correct implicit scopes for abstract types) (#1293)
  * Add cats/bio instances for `Lifecycle` (#1280)
  * Feature: Free monad (#1278)
  * Fix #1274 remove BIO* prefix from all BIO classes and switch to a new naming scheme (#1276)
  * Fix #1275: rename `DIEffect` to `QuasiEffect` (#1292)
  * Add `make[X].addDependencies` and `modify[X].addDependencies` syntax in ModuleDef (#1288)
  * Make `Lifecycle#flatMap` interruptible (#1286)
  * Make `Lifecycle.liftF` & `Lifecycle.LiftF` interruptible (#1284)
  * Documentation yak shave (#1283)
  * Fix mutators overriding normal bindings and each other. Add mutator syntax to syntax reference. Mention that javax.inject.Named is supported in docs. (#1272)
  * remove ListSet (#1273)
  * Fix #894: remove http4s dependency in microsite, use a command-line calculator as an example instead. (#1271)
  * Feature/memoization levels (#1251)
  * Document Lifecycle inheritance helpers, document using them to workaround for implicit injection, `acquire`/`release`/`extract` methods. Add `Lifecycle#widen`/`#widenF` (#1270)
  * Re-add `bootstrapPluginConfig` field to `RoleAppMain` (#1268)
  * Turns out there's no such word as "overriden". Rename `overridenBy` to `overriddenBy` (double D) (#1263)
  * Test built-in BIO* instances using cats-effect Laws  (#1249)
  * Fix #1138:  Preserve interruption when converting ZManaged/Resource to `Lifecycle` (#1258)
  * Rename `DIResourceBase` to `Lifecycle` (#1247)
  * Add Tele2 to users (#1257)
  * Sort docker networks by `id` when trying to reuse network, add more logging to container/network reuse (#1256)
  * Fix #1073 Assert that effect type is compatible in `OrderedPlan#assertValid`. Rename `assertImportsResolvedOrThrow` -> `assertValid` (#1254)
  * Include support module for chosen effect type automatically, deprecate old `XDIEffectModule`'s. Fix #1015 move support modules for effect types from distage-framework to distage-core,  (#1235)
  * Implement `BIOPrimitives` for `monix-bio` (#1246)
  * Add links to test suites written in `distage-testkit` to docs (#1244)
  * distage-testkit: Add `AssertSync` (#1243)
  * wip #1225 (#1242)
  * Add BIOConcurrent between BIOAsync & BIOParallel (#1240)
  * Re-add Launchers[F] to RoleAppMain (#1241)
  * Fix #1051 Move BIOTemporal to a side-hierarchy (#1236)
  * Update distage-testkit example to latest version from `distage-example` (now includes `Scene.Managed` example) (#1237)
  * testkit memoization and parallel execution docs (#1200)
  * Add missing BIOParallel's Monad syntax (#1234)
  * Fix monix-bio BIOTemporal instance: count the time correctly, require cats-effect Timer instead of Clock2 (available in monix-bio by default); fix BIOTemporal instance resolution (#1231)
  * Allow non-Throwable errors in `BIOError#withFilter`, include source position in error message (#1230)
  * Fix #1054: rename `BIOMonadError`->`BIOError`, downgrade BIOApplicativeError to an Applicative on both parameters, instead of a Monad on the error (#1229)
  * Convenient plugins that handles cats typeclasses. (#1227)
  * Update definition of `race`/`racePair` for ZIO to match current interop-cats (#1228)
  * Fix #1178: preserve `@Id` annotation in higher-kinded type aliases (#1209)
  * Feature: identity compat for integration checks (#1157)
  * Rename `Injector.apply` with activation parameter to `Injector.withBootstrapActivation` since the passed activation is no longer applied to non-bootstrap modules (#1222)
  * Use axis defaults from #1030 in `BootstrapLocator` (#1223)
  * Rename `World.Dummy` back to `World.Mock` (#1224)
  * Role-based application uses Injector to bootstrap (#1208)
  * Fix #1204 synchronize default LogstageCodec for Throwable with LogstageCirceRenderingPolicy implementation for Throwable (#1206)
  * Fix #1201 LogstageCodec for Boolean (#1202)
  * Add `DIResource.unit`, make `DIResource.pure` polymorphic in `F` (#1198)
  * add `BIORunner#unsafeRunAsyncAsFuture`, deprecate `AsEither` suffixes (#1199)
  * Monix BIO instances (#996)
  * Support incremental compilation in `StaticPluginChecker` compile-time checking macro (#1194)
  * distage testkit docs part 1 (#1145)
  * ConfigWriter fixes: fix regression when config minimization happened before GC, removed ConfigPathExtractor as it can seemingly no longer work with new `trisect` implementation, add band-aid to ConfigWriter to always include "activation" section before #779 (#1177)
  * docker-global-reuse followup: Merge GlobalDockerReusePolicy & DockerReusePolicy; fix ContainerNetworkDef reuse & IntegrationCheckException (#1159)
  * WIP: Adapt distage-testkit to 0.11.0 Activation API changes (#1143)
  * locator can be accessed outside of launcher (#1147)
  * ProviderMagnet -> Functoid (#1148)
  * plugin scanning done in compile time (#1139)
  * Feature/flexible reuse (#1146)
  * provisioner collects operation timings (#1137)
  * Use F[_] in integraion check. (#1152)
  * Fix #1124 NullPointerException in LogstageCirceRenderingPolicy when Throwable#getMessage returns null (e.g. in NullPointerException) (#1149)
  * 0.11.0: Mutators (#845)
