---
out: index.html
---
Izumi Project
=============

![izumi-logo](media/izumi-logo-full-purple.png)

Izumi (jp. *泉水*, *spring*) is a set of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.

including the following components:

1. @ref[**distage**](distage/00_distage.md) – Staged, transparent and debuggable runtime & compile-time Dependency Injection Framework,
2. @ref[**logstage**](logstage/00_logstage.md) – Automatic structural logs from Scala string interpolations,
3. @ref[**idealingua**](idealingua/00_idealingua.md) – API Definition, Data Modeling and RPC Language, optimized for fast prototyping – like gRPC, but with a human face. Currently generates servers and clients for Go, TypeScript, C# and Scala,
4. @ref[**Opinionated SBT plugins**](sbt/00_sbt.md) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven),
5. @ref[**Percept-Plan-Execute-Repeat (PPER)**](pper/00_pper.md) – a pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.

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
  // Configure LogStage with Typesafe Config
  "io.7mind.izumi" %% "logstage-config" % "$izumi.version$",
  // Write logs as JSON
  "io.7mind.izumi" %% "logstage-rendering-circe " % "$izumi.version$",
  // Route Slf4J logs to LogStage
  "io.7mind.izumi" %% "logstage-adapter-slf4j " % "$izumi.version$",
  // Route LogStage logs to Slf4J
  "io.7mind.izumi" %% "logstage-sink-slf4j " % "$izumi.version$",
  
  // Idealingua Runtime Dependencies (for use with the Idealingua compiler)
  "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-http4s" % "$izumi.version$",
)
```
@@@

@scaladoc[Scaladoc](izumi.index)

Izumi on [GitHub](https://github.com/7mind/izumi)

Latest SNAPSHOT [documentation](https://izumi.7mind.io/latest/snapshot/doc/)

@@@ index

* [distage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
* [Idealingua](idealingua/00_idealingua.md)
* [SBT Plugins](sbt/00_sbt.md)
* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)

@@@

Credits
=======

![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools 
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/) 
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and 
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/).
