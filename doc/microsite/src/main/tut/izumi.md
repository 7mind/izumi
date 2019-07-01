---
out: index.html
---
Izumi Project
=============

Izumi (*jap. 泉水, spring*) is a set of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.

including the following components:

1. @ref[**distage**](distage/00_distage.md) – Staged, transparent and debuggable runtime & compile-time Dependency Injection Framework,
2. @ref[**logstage**](logstage/00_logstage.md) – Automatic structural logs from Scala string interpolations,
3. @ref[**idealingua**](idealingua/00_idealingua.md) – API Definition, Data Modeling and RPC Language, optimized for fast prototyping – like gRPC, but with a human face. Currently generates servers and clients for Go, TypeScript, C# and Scala,
4. @ref[**Opinionated SBT plugins**](sbt/00_sbt.md) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven),
5. @ref[**Percept-Plan-Execute-Repeat (PPER)**](pper/00_pper.md) – a pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.

Dependencies
------------

To use @ref[Izumi SBT Toolkit](sbt/00_sbt.md) add the following into `project/build.sbt`:

@@@vars
```scala
val izumi_version = "$izumi.version$"

// sbt toolkit
addSbtPlugin("io.7mind.izumi" % "sbt-izumi" % izumi_version)

// This is Izumi Bill of Materials, see below
addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % izumi_version)
```
@@@


You can use izumi's `BOM` definitions to import (from @ref[`sbt-izumi-deps` plugin](sbt/00_sbt.md#bills-of-materials)). BOM will insert the correct version automatically:

@ref[**distage**](distage/00_distage.md) modules:

```scala
libraryDependencies ++= Seq(
  Izumi.R.distage_core, // Core DIStage library
  Izumi.T.distage_testkit,  // Testkit for ScalaTest
  Izumi.R.distage_static, // Compile-time checks & reflection-less mode
  Izumi.R.distage_config, // Typesafe Config support
  Izumi.R.distage_plugins, // Classpath discovery support
  Izumi.R.distage_roles,  // Roles & Application entrypoint framework
)
```

@ref[**logstage**](logstage/00_logstage.md) modules:

```scala
libraryDependencies ++= Seq(
  Izumi.R.logstage_core, // Core LogStage library
  Izumi.R.logstage_config, // Configure LogStage with Typesafe Config
  Izumi.R.logstage_di, // LogStage integration with DIStage
  Izumi.R.logstage_adapter_slf4j, // Route Slf4J logs to LogStage
  Izumi.R.logstage_sink_slf4j, // Route LogStage logs to Slf4J
  Izumi.R.logstage_rendering_circe, // Write logs as JSON
)
```

@ref[**IdeaLingua**](idealingua/00_idealingua.md) modules:

```scala
// Idealingua Runtime Dependencies (for use with the Idealingua compiler)
libraryDependencies ++= Seq(
  Izumi.R.idealingua_runtime_rpc_http4s
)
```

Alternatively, you can use the following artifact names and versions:

@@@vars
```scala
libraryDependencies ++= Seq(
  "io.7mind.izumi" %% "distage-core" % "$izumi.version$",
  "io.7mind.izumi" %% "distage-testkit" % "$izumi.version$" % Test,
  "io.7mind.izumi" %% "distage-static" % "$izumi.version$",
  "io.7mind.izumi" %% "distage-config" % "$izumi.version$",
  "io.7mind.izumi" %% "distage-plugins" % "$izumi.version$",
  "io.7mind.izumi" %% "distage-roles" % "$izumi.version$",

  "io.7mind.izumi" %% "logstage-core" % "$izumi.version$",
  "io.7mind.izumi" %% "logstage-config" % "$izumi.version$",
  "io.7mind.izumi" %% "logstage-di" % "$izumi.version$",
  "io.7mind.izumi" %% "logstage-adapter-slf4j " % "$izumi.version$",
  "io.7mind.izumi" %% "logstage-sink-slf4j " % "$izumi.version$",
  "io.7mind.izumi" %% "logstage-rendering-circe " % "$izumi.version$",
  
  "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-http4s" % "$izumi.version$",
)
```
@@@

You can find ScalaDoc API docs @scaladoc[here](izumi.index)

You can find Izumi on github [here](https://github.com/7mind/izumi)

Latest SNAPSHOT documentation [here](https://izumi.7mind.io/latest/snapshot/doc/)

@@@ index

* [Idealingua](idealingua/00_idealingua.md)
* [distage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
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
