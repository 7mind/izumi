---
out: index.html
---
Izumi Project
=============

![izumi-logo](media/izumi-logo-full-purple.png)

Izumi (*jp. 泉水, spring*) is an ecosystem of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.

including the following components:

1. @ref[**distage**](distage/00_distage.md) – Transparent and debuggable Dependency Injection framework for pure FP Scala,
2. @ref[**distage-testkit**](distage/distage-testkit.md) – Hyper-pragmatic pure FP Test framework. Shares heavy resources globally across all test suites; lets you easily swap implementations of component; uses your effect type for parallelism.
3. @ref[**distage-framework-docker**](distage/distage-framework-docker.md) – A distage extension for using docker containers in tests or for local application runs, comes with example Postgres, Cassandra, Kafka & DynamoDB containers.
4. @ref[**LogStage**](logstage/00_logstage.md) – Automatic structural logs from Scala string interpolations,
5. @ref[**BIO**](bio/00_bio.md) - A typeclass hierarchy for tagless final style with Bifunctor and Trifunctor effect types. Focused on ergonomics and ease of use with zero boilerplate.
6. [**izumi-reflect**](https://github.com/zio/izumi-reflect) (moved to [zio/izumi-reflect](https://github.com/zio/izumi-reflect)) - Portable, lightweight and kind-polymorphic alternative to `scala-reflect`'s Typetag for Scala, Scala.js, Scala Native and ([soon](https://github.com/7mind/dotty-typetag-research)) Dotty
7. @ref[**IdeaLingua**](idealingua/00_idealingua.md) (moved to [7mind/idealingua-v1](https://github.com/7mind/idealingua-v1)) – API Definition, Data Modeling and RPC language, optimized for fast prototyping – like gRPC or Swagger, but with a human face. Generates RPC servers and clients for Go, TypeScript, C# and Scala,
8. @ref[**Opinionated SBT plugins**](sbt/00_sbt.md) (moved to [7mind/sbtgen](https://github.com/7mind/sbtgen)) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven)
9. @ref[**Percept-Plan-Execute-Repeat (PPER)**](pper/00_pper.md) – A pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.


Dependencies
------------

To use, add the following into `project/build.sbt`,

@@@vars

```scala
libraryDependencies ++= Seq(
  // distage core library
  "io.7mind.izumi" %% "distage-core" % "$izumi.version$",
  // distage-testkit for ScalaTest
  "io.7mind.izumi" %% "distage-testkit-scalatest" % "$izumi.version$" % Test,
  // distage-framework: Roles, Entrypoints, Effect modules
  "io.7mind.izumi" %% "distage-framework" % "$izumi.version$",
  // Typesafe Config support
  "io.7mind.izumi" %% "distage-extension-config" % "$izumi.version$",
  // Classpath discovery support
  "io.7mind.izumi" %% "distage-extension-plugins" % "$izumi.version$",
  // LogStage integration with DIStage
  "io.7mind.izumi" %% "distage-extension-logstage" % "$izumi.version$",
  
  // LogStage core library
  "io.7mind.izumi" %% "logstage-core" % "$izumi.version$",
  // Write logs as JSON
  "io.7mind.izumi" %% "logstage-rendering-circe " % "$izumi.version$",
  // Route Slf4J logs to LogStage
  "io.7mind.izumi" %% "logstage-adapter-slf4j " % "$izumi.version$",
  // Route LogStage logs to Slf4J
  "io.7mind.izumi" %% "logstage-sink-slf4j " % "$izumi.version$",

)
```
@@@

Development
-----------

* [GitHub](https://github.com/7mind/izumi)

Documentation
-------------

* @scaladoc[Scaladoc](izumi.index)
* [Documentation](https://izumi.7mind.io/)
* SNAPSHOT [documentation](https://izumi.7mind.io/latest/snapshot/)

Further reading
---------------

Example projects:

* [DIStage Example Project](https://github.com/7mind/distage-example)
* [Idealingua Example Project with TypeScript and Scala](https://github.com/7mind/idealingua-example)

Support Chats:

* [Izumi on Gitter](https://gitter.im/7mind/izumi)
* [Izumi User Group [RU] on Telegram](https://t.me/izumi_ru)
* [Izumi User Group [EN] on Telegram](https://t.me/izumi_en)

Slides:

* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.slideshare.net/7mind/hyperpragmatic-pure-fp-testing-with-distagetestkit)
* [distage: Staged Dependency Injection](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
* [logstage: Zero-cost Structured Logging](https://www.slideshare.net/7mind/logstage-zerocosttructuredlogging)
* [More slides](https://github.com/7mind/slides)

Videos:

* [Hyper-pragmatic Pure FP Testing with distage-testkit, Functional Scala, London](https://www.youtube.com/watch?v=CzpvjkUukAs)
* [Livecoding: DIStage & Bifunctor Tagless Final on Youtube](https://www.youtube.com/watch?v=C0srg5T0E4o&t=4971)

Credits
=======

![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools 
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/) 
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and 
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

@@toc { depth=2 }

@@@ index

* [distage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
* [BIO](bio/00_bio.md)
* [Idealingua](idealingua/00_idealingua.md)
* [SBT Plugins](sbt/00_sbt.md)
* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)

@@@
