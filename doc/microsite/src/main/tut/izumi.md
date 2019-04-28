---
out: index.html
---
Izumi Project
=============

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

[Github](https://github.com/7mind/izumi)

Dependencies
------------

To use @ref[Izumi SBT Toolkit](sbt/00_sbt.md) add the following into `project/build.sbt`:

@@@vars
```scala
val izumi_version = "$izumi.version$"
// sbt toolkit
addSbtPlugin("io.7mind.izumi" % "sbt-izumi" % izumi_version)

// This is Izumi's BOM (Bill of Materials), see below
addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % izumi_version)

// idealingua compiler (optional)
addSbtPlugin("io.7mind.izumi" % "sbt-idealingua" % izumi_version)
```
@@@


You can use izumi's `BOM` definitions to import (from @ref[`sbt-izumi-deps` plugin](sbt/00_sbt.md#bills-of-materials)). BOM will insert the correct version automatically:

```scala
libraryDependencies ++= Seq(
  
  // distage
    Izumi.R.distage_core
  , Izumi.R.distage_config // Typesafe Config support
  , Izumi.R.distage_cats // Cats Integration
  , Izumi.R.distage_static // Compile-time checks & reflection-less mode
  , Izumi.R.distage_plugins // runtime Plugins support
  , Izumi.R.logstage_di // LogStage integration
  , Izumi.R.distage_app  // DiApp
  , Izumi.R.distage_roles  // Roles
  
  // LogStage
  , Izumi.R.logstage_core
  , Izumi.R.logstage_zio // ZIO Integration (log current FiberId)
  , Izumi.R.logstage_cats // Cats Integration
  , Izumi.R.logstage_adapter_slf4j // Route Slf4J logs to logstage
  , Izumi.R.logstage_rendering_circe // dump structured log as JSON
  , Izumi.R.logstage_sink_slf4j // write to slf4j
  
  // Idealingua Runtime Dependencies (for use with Idealingua compiler)
  , Izumi.R.idealingua_runtime_rpc_http4s
  , Izumi.R.idealingua_runtime_rpc_scala
  , Izumi.R.idealingua_model
)
```

Alternatively, you can use the following artifact names and versions:

@@@vars
```scala
libraryDependencies ++= Seq(
    "io.7mind.izumi" %% "distage-core" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-config" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-cats" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-testkit" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-plugins" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-static" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-app" % "$izumi.version$"
  , "io.7mind.izumi" %% "distage-roles" % "$izumi.version$"
  
  , "io.7mind.izumi" %% "logstage-core" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-adapter-slf4j " % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-config" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-config-di" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-cats" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-zio" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-api" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-di" % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-rendering-circe " % "$izumi.version$"
  , "io.7mind.izumi" %% "logstage-sink-slf4j " % "$izumi.version$"
  
  , "io.7mind.izumi" %% "idealingua-v1-model" % "$izumi.version$"
  , "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-scala" % "$izumi.version$"
  , "io.7mind.izumi" %% "idealingua-v1-runtime-rpc-http4s" % "$izumi.version$"
)
```
@@@

You can find ScalaDoc API docs @scaladoc[here](izumi.index)

You can find Izumi on github [here](https://github.com/7mind/izumi)

@@@ index

* [Idealingua](idealingua/00_idealingua.md)
* [DiStage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
* [SBT Plugins](sbt/00_sbt.md)
* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)

@@@

