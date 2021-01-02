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

# Major Features since 0.10.18

[comment]: <> (Welcome to 1.0 release of Izumi. There's a number of major additions that make it into this release.)

[comment]: <> (Major changes across Izumi libraries.)

Below you may find description of the major changes that occurred since 0.10.18

[comment]: <> (You may also find a general, non-technical overview of the release in the talk [Izumi 1.0: Your Next Scala Stack]&#40;https:&#41;)

description: motivation: details of the change

## distage

### Compile-time safety

Total compile-time safety lands for `distage` in 1.0, although previously we thought it was impossible to implement!

The new compile-time checks provide fast feedback during development and remove the need to launch tests or launch application itself or write special tests to check the correctness of wiring.

For users of `distage-extension-config`, config parsing will be verified at compile-time against the default config files in resource folder.
There is an option to disable config checking or check against a specific resource file instead of defaults.

Checking is not enabled out-of-the-box, you must enable it by adding a “trigger” object in test scope. This object will emit compile-time errors for any issues or omissions in your `ModuleDefs`. It will recompile itself as necessary to provide feedback during development.

See [“Compile-time checks” chapter](https://izumi.7mind.io/distage/distage-framework#compile-time-checks) for details.

### Other additions

#### Compile-time plugin discovery

`distage` supports classpath discovery of modules through an extension. Before 1.0, this extension was hard to use in Graal Native Image applications due to reliance on runtime reflection in the implementation.

Since 1.0 you can perform plugin scanning at compile-time using [`PluginConfig.compileTime`](https://izumi.7mind.io/api/izumi/distage/plugins/PluginConfig$.html#compileTime(pluginsPackage:String):izumi.distage.plugins.PluginConfig).

Compile-time scanning enables plugin-based development workflow on platforms without runtime reflection, such as Graal Native, however the runtime extension aspect of plugins is lost.

See ["Compile-time scanning" chapter](https://izumi.7mind.io/distage/distage-framework#compile-time-scanning) for details.

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

#### Changes in Activation behavior

In general:

For Sets:

<pre>
    3. DefaultModule
    4. Reworked Role Launcher
    6. Other core breaking changes

    ## distage-testkit

    1. Multi-Level Memoization
    2. Documentation by CoreyOConnor

    ## BIO

    1. BIO changes

    ## logstage

    1. renames to follow BIO

    ## Contributions

    1. other pull requests,

    ### Memoization Levels

    (PR #1251, Fix for #1188)

    # Other Changes

    ### distage-framework

    * Fix #1056 Allow effects in`IntegrationCheck` by @Caparow (https://github.com/7mind/izumi/pull/1152, https://github.com/7mind/izumi/pull/1157)

    ### distage-framework-docker

    ### distage-testkit

    ### distage-core

    Nth, the distage API changes:
        Injector: always requires [F] parameter, Activations are passed in PlannerInput
        renames: ProviderMagnet->Functoid, DIResource->Lifecycle
        DefaultModule
        PlanVerifier

    * Fix #1196 Cats/BIO instances for `Lifecycle` by @VladPodilnyk (#1280)

    ### BIO

    * Add `Free`, `FreeError`, `FreePanic` data types, free structures for Monad/Error/Panic typeclasses respectively, by @Caparow (#1278)
    * Fix #1245 Discipline Law Tests by @VladPodilnyk (#1249)


    ## LogStage

</pre>

# Pull Requests merged since 0.10.18

  * Fix #1226 Finalize static plan checker API (#1296)
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
