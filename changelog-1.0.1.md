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

# Changes since 1.0.0

### distage

  * Fixed performance issues and a bug in handling of weak set bindings in compile-time checker

### LogStage

  * Add extension methods `.fail` & `.terminate` to `LogIO2`, they support a pattern of logging an error message and immediately failing or terminating with the same message (#1351)

# Pull Requests merged since 1.0.0

  * Add `LogIO2#fail`, `LogIO2#terminate` utility methods (#1351)
  * Qualify names in IzArtifactMaterializer (#1339)
  * Disable searching roles by subtype if `-Dizumi.distage.roles.reflection=false`, reduces launch time by omitting unnecessary work (#1349)
