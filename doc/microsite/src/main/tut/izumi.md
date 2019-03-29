---
out: index.html
---
Izumi Project
=============

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

[Github](https://github.com/pshirshov/izumi-r2)

Dependencies
------------

To use @ref[Izumi SBT Toolkit](sbt/00_sbt.md) add the following into `project/build.sbt`:

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
    "com.github.pshirshov.izumi.r2" %% "distage-core" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-config" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-cats" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-testkit" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-plugins" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-static" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-app" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "distage-roles" % "$izumi.version$"
  
  , "com.github.pshirshov.izumi.r2" %% "logstage-core" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-adapter-slf4j " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-config" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-config-di" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-cats" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-zio" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-api" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-di" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-rendering-circe " % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "logstage-sink-slf4j " % "$izumi.version$"
  
  , "com.github.pshirshov.izumi.r2" %% "idealingua-v1-model" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "idealingua-v1-runtime-rpc-scala" % "$izumi.version$"
  , "com.github.pshirshov.izumi.r2" %% "idealingua-v1-runtime-rpc-http4s" % "$izumi.version$"
)
```
@@@

You can find ScalaDoc API docs @scaladoc[here](izumi.index)

You can find Izumi on github [here](https://github.com/pshirshov/izumi-r2)

@@@ index

* [Idealingua](idealingua/00_idealingua.md)
* [DiStage](distage/00_distage.md)
* [LogStage](logstage/00_logstage.md)
* [SBT Plugins](sbt/00_sbt.md)
* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)

@@@

