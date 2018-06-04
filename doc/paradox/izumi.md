---
out: index.html
---
Izumi Toolkit
=============

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

Dependencies
------------

To use [Izumi SBT Toolkit](sbt/00_sbt.md) add the follwing into `project/build.sbt`:

@@@vars
```scala
val izumi_version = "$izumi.version$"
// sbt toolkit
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi" % izumi_version)

// This is a Izumi's BOM (Bill of Materials), see below
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi-deps" % izumi_version)

// idealingua compiler
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-idealingua" % izumi_version)
```
@@@

@@@ warning { title='TODO' }
Put list of modules here
@@@

You may find ScalaDoc API docs @scaladoc[here](izumi.index)

@@@ index

* [Productivity and challenges](manifesto/00_manifesto.md)
* [PPER](pper/00_pper.md)
* [DiStage](distage/00_distage.md)
* [Idealingua](idealingua/00_idealingua.md)
* [LogStage](logstage/00_logstage.md)
* [SBT Plugins](sbt/00_sbt.md)

@@@
