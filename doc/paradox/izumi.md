---
out: index.html
---
Izumi Toolkit
=============

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

[Github](https://github.com/pshirshov/izumi-r2)

Dependencies
------------

To use @ref[Izumi SBT Toolkit](sbt/00_sbt.md) add the follwing into `project/build.sbt`:

@@@vars
```scala
val izumi_version = "$izumi.version$"
// sbt toolkit
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi" % izumi_version)

// This is Izumi's BOM (Bill of Materials), see below
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi-deps" % izumi_version)

// idealingua compiler (optional)
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-idealingua" % izumi_version)
```
@@@


You can use izumi's `BOM` definitions to import (it comes with the `sbt-izumi-deps plugin`). BOM will insert the correct version automatically:

```scala
libraryDependencies ++= Seq(
  
  // distage
    Izumi.R.distage_core
  , Izumi.R.distage_config // Typesafe Config support
  , Izumi.R.distage_cats // Cats Integration
  , Izumi.R.distage_static // Compile-time checks & reflection-less mode
  , Izumi.R.distage_plugins // runtime Plugins support
  , Izumi.R.distage_app  // DiApp
  , Izumi.R.logstage_di // LogStage integration
  
  // LogStage
  , Izumi.R.logstage_api_logger
  , Izumi.R.logstage_adapter_slf4j // Route Slf4J logs to logstage
  , Izumi.R.logstage_rendering_json4s // dump structured log as JSON
  , Izumi.R.logstage_sink_console 
  , Izumi.R.logstage_sink_file // write to files with log rotation support
  , Izumi.R.logstage_sink_slf4j // write to slf4j
  
  // Idealingua Runtime Dependencies (for use with Idealingua compiler)
  , Izumi.R.idealingua_model
  , Izumi.R.idealingua_runtime_rpc_http4s
  , Izumi.R.idealingua_runtime_rpc_circe
  , Izumi.R.idealingua_runtime_rpc_cats
)
```

Alternatively, you can list artifact names and versions manually:

@@@vars
```scala
libraryDependencies ++= Seq(
    "com.github.pshirshov.izumi.r2" %% "distage-core" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-config " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-cats " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-static " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-plugins " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-app  " % "$izumi.version$"
  
  , "com.github.pshirshov.izumi.r2" %% "logstage-di " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-api-logger" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-adapter-slf4j " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-rendering-json4s " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-sink-console " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-sink-file " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-sink-slf4j " % "$izumi.version$"
  
  , "com.github.pshirshov.izumi.r2" %% "idealingua-model" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-http4s" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-circe" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "idealingua-runtime-rpc-cats" % "$izumi.version$"
)
```
@@@

You may find ScalaDoc API docs @scaladoc[here](izumi.index)

You may find Izumi on github [here](https://github.com/pshirshov/izumi-r2)

@@toc { depth=2 }

@@@ index

* [Idealingua](idealingua/00_idealingua.md)
* [DiStage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
* [SBT Plugins](sbt/00_sbt.md)
* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)

@@@

