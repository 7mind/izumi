---
out: index.html
---
SBT Notes
=========

Multi-modal builds
------------------

@@@ warning { title='MOVED' }
Izumi used to provide many SBT plugins, though all the funcitonality had been moved into a separate project, [sbtgen](https://github.com/7mind/sbtgen/), which is an SBT project generator
@@@

Setting up SBT
--------------

`distage-framework` relies on build-time meta information in order to provide useful diagnostic messages during application startup.

It's adviced to provide the relevant parameters as compiler's "macro settings".

Add `sbt-git` into your `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1")
```

Then add the build metainformation into your `build.sbt`:

```scala
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:git-repo-clean=${git.gitUncommittedChanges.value}",
      s"-Xmacro-settings:git-branch=${git.gitCurrentBranch.value}",
      s"-Xmacro-settings:git-described-version=${git.gitDescribedVersion.value.getOrElse("")}",
      s"-Xmacro-settings:git-head-commit=${git.gitHeadCommit.value.getOrElse("")}",
    )
```

Please note that `version` and `organization` may vary between artifacts and `name` always will. If that's the case in your build, configure these options on per-artifact basis.

Bills of materials
------------------


Izumi ships with a bill-of-materials artifact. Add the following into your
into your `project/plugins.sbt`:

@@@vars
```scala
addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % "$izumi.version$")
```

After that you may easily reference various constants related to Izumi build:

```scala
"io.7mind.izumi" %% "distage-core" % Izumi.version
```

`sbtgen` allows you to generate bills of materials for your own projects.
