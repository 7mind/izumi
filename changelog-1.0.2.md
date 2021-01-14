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

# Changes since 1.0.1

### distage

  * Documentation updates and new documentation chapters.
  * Rename `PlannerInput.noGC`->`.everything`, add constructors with NonEmptySet for Roots/PlannerInput, fix Roots scaladoc, add `NonEmptySet#widen` (#1355)
  * Add `Blocker` binding to cats.effect.IO's default module (#1362)
  * Change `Lifecycle#mapK` to accept `bio.Morphism1` instead of `cats.FunctionK` (still source compatible due to conversion) (#1363)

### distage-framework-docker

  * add `DockerContainer#dependOnContainerPorts` shortcut, makes it easier to expose ports as env vars in a container (#1360)
    * rename `dependOnDocker`->`dependOnContainer` for consistency, old name is deprecate

### fundamentals-platform

  * Show macro settings errors as `info` instead of `warning` (#1361)

# Pull Requests merged since 1.0.1

    * WIP 1.0 release notes (#1358)
    * add `DockerContainer#dependOnContainerPorts` shortcut, makes it easier to expose ports as env vars in a container, rename `dependOnDocker`->`dependOnContainer` (#1360)
    * Show macro settings errors as `info` instead of `warning` (#1361)
    * Change `Lifecycle#mapK` to accept `bio.Morphism1` instead of `cats.FunctionK` (still source compatible due to conversion) (#1363)
    * Add `Blocker` binding to cats.effect.IO's default module (#1362)
    * Make all tests in `distage-testkit` docs run, improve examples, rewrite Out-of-the-box-typeclass instances chapter, Make `ZIO Has Bindings` example more understandable (#1357)
    * Fix `DefaultModule` for ZIO+Cats, include cats-effect typeclass instances when `interop-cats` is on the classpath, not just when `cats-effect` is on the classpath (#1356)
    * rename `PlannerInput.noGC`->`.everything`, add constructors with NonEmptySet for Roots/PlannerInput, fix Roots scaladoc, add `NonEmptySet#widen` (#1355)
    * Avoid constructing Set in ModuleDefDSL `include` (#1353)
    * Allow chaining after ModuleDefDSL `todo` keyword (#1352)
