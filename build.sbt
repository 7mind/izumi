import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

import com.typesafe.sbt.SbtGit.GitKeys._

enablePlugins(SbtgenVerificationPlugin)

disablePlugins(AssemblyPlugin)

lazy val `fundamentals-collections` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-collections"))
  .dependsOn(
    `fundamentals-functional` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-collectionsJVM` = `fundamentals-collections`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-collectionsJS` = `fundamentals-collections`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-platform` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-platform"))
  .dependsOn(
    `fundamentals-language` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) },
    Test / npmDependencies ++= Seq(
      (  "hash.js",  "1.1.7")
    )
  )
lazy val `fundamentals-platformJVM` = `fundamentals-platform`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-platformJS` = `fundamentals-platform`.js
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-language` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-language"))
  .dependsOn(
    `fundamentals-literals` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-languageJVM` = `fundamentals-language`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-languageJS` = `fundamentals-language`.js
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-reflection` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-reflection"))
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-reflectionJVM` = `fundamentals-reflection`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-reflectionJS` = `fundamentals-reflection`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-functional` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-functional"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-functionalJVM` = `fundamentals-functional`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-functionalJS` = `fundamentals-functional`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-bio` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-bio"))
  .dependsOn(
    `fundamentals-language` % "test->compile;compile->compile",
    `fundamentals-orphans` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Optional,
      "io.monix" %%% "monix" % V.monix % Optional,
      "io.monix" %%% "monix-bio" % V.monix_bio % Optional,
      "org.typelevel" %%% "cats-effect-laws" % V.cats_effect % Test,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "discipline-core" % V.discipline % Test,
      "org.typelevel" %%% "discipline-scalatest" % V.discipline_scalatest % Test,
      "dev.zio" %%% "zio-interop-cats" % V.zio_interop_cats % Test excludeAll("dev.zio" %% "izumi-reflect")
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-bioJVM` = `fundamentals-bio`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-bioJS` = `fundamentals-bio`.js
  .settings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % V.scala_java_time % Test
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-json-circe` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-json-circe"))
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-derivation" % V.circe_derivation,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "org.typelevel" %% "jawn-parser" % V.jawn % Test,
      "io.circe" %%% "circe-literal" % V.circe % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-json-circeJVM` = `fundamentals-json-circe`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-json-circeJS` = `fundamentals-json-circe`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-orphans` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-orphans"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Optional,
      "io.monix" %%% "monix" % V.monix % Optional,
      "io.monix" %%% "monix-bio" % V.monix_bio % Optional,
      "dev.zio" %%% "zio-interop-cats" % V.zio_interop_cats % Optional excludeAll("dev.zio" %% "izumi-reflect")
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-orphansJVM` = `fundamentals-orphans`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-orphansJS` = `fundamentals-orphans`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-literals` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-literals"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `fundamentals-literalsJVM` = `fundamentals-literals`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-literalsJS` = `fundamentals-literals`.js
  .disablePlugins(AssemblyPlugin)

lazy val `distage-core-api` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("distage/distage-core-api"))
  .dependsOn(
    `fundamentals-reflection` % "test->compile;compile->compile",
    `fundamentals-bio` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Optional,
      "org.typelevel" %%% "cats-core" % V.cats % Test,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Test,
      "dev.zio" %%% "zio" % V.zio % Test excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Test,
      "io.monix" %%% "monix-bio" % V.monix_bio % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `distage-core-apiJVM` = `distage-core-api`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `distage-core-apiJS` = `distage-core-api`.js
  .disablePlugins(AssemblyPlugin)

lazy val `distage-core-proxy-cglib` = project.in(file("distage/distage-core-proxy-cglib"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "cglib" % "cglib-nodep" % V.cglib_nodep
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-core` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("distage/distage-core"))
  .dependsOn(
    `distage-core-api` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Optional,
      "io.monix" %%% "monix" % V.monix % Optional,
      "io.monix" %%% "monix-bio" % V.monix_bio % Optional,
      "dev.zio" %%% "zio-interop-cats" % V.zio_interop_cats % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "javax.inject" % "javax.inject" % "1" % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `distage-coreJVM` = `distage-core`.jvm
  .dependsOn(
    `distage-core-proxy-cglib` % "test->compile;compile->compile"
  )
  .disablePlugins(AssemblyPlugin)
lazy val `distage-coreJS` = `distage-core`.js
  .settings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % V.scala_java_time % Test
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-extension-config` = project.in(file("distage/distage-extension-config"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "com.github.pureconfig" %% "pureconfig-magnolia" % V.pureconfig,
      "com.propensive" %% "magnolia" % V.magnolia,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-extension-plugins` = project.in(file("distage/distage-extension-plugins"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile",
    `distage-extension-config` % "test->compile",
    `logstage-coreJVM` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "io.github.classgraph" % "classgraph" % V.classgraph,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-extension-logstage` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("distage/distage-extension-logstage"))
  .dependsOn(
    `distage-core-api` % "test->compile;compile->compile",
    `distage-core` % "test->compile",
    `logstage-core` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `distage-extension-logstageJVM` = `distage-extension-logstage`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `distage-extension-logstageJS` = `distage-extension-logstage`.js
  .disablePlugins(AssemblyPlugin)

lazy val `distage-framework-api` = project.in(file("distage/distage-framework-api"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-framework` = project.in(file("distage/distage-framework"))
  .dependsOn(
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `logstage-rendering-circeJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile;compile->compile",
    `distage-framework-api` % "test->compile;compile->compile",
    `distage-extension-plugins` % "test->compile;compile->compile",
    `distage-extension-config` % "test->compile;compile->compile",
    `distage-extension-plugins` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "org.typelevel" %% "cats-core" % V.cats % Test,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Test,
      "dev.zio" %% "zio" % V.zio % Test excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %% "izumi-reflect" % V.izumi_reflect % Test,
      "io.monix" %% "monix-bio" % V.monix_bio % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / unmanagedSourceDirectories ++= (Compile / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    },
    Test / unmanagedSourceDirectories ++= (Test / unmanagedSourceDirectories).value.flatMap {
      dir =>
       val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
       def scalaDir(s: String) = file(dir.getPath + s)
       (partialVersion match {
         case Some((2, n)) => Seq(scalaDir("_2"), scalaDir("_2." + n.toString))
         case Some((x, n)) => Seq(scalaDir("_3"), scalaDir("_" + x.toString + "." + n.toString))
         case None         => Seq.empty
       })
    }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-framework-docker` = project.in(file("distage/distage-framework-docker"))
  .dependsOn(
    `distage-coreJVM` % "test->compile;compile->compile",
    `distage-extension-config` % "test->compile;compile->compile",
    `distage-framework-api` % "test->compile;compile->compile",
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `distage-testkit-scalatest` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Test,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Test,
      "dev.zio" %% "zio" % V.zio % Test excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %% "izumi-reflect" % V.izumi_reflect % Test,
      "io.monix" %% "monix-bio" % V.monix_bio % Test,
      "com.github.docker-java" % "docker-java-core" % V.docker_java,
      "com.github.docker-java" % "docker-java-transport-zerodep" % V.docker_java
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-testkit-core` = project.in(file("distage/distage-testkit-core"))
  .dependsOn(
    `distage-framework` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-testkit-scalatest` = project.in(file("distage/distage-testkit-scalatest"))
  .dependsOn(
    `distage-testkit-core` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile;compile->compile",
    `distage-extension-plugins` % "test->compile;compile->compile",
    `distage-framework` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %% "izumi-reflect" % V.izumi_reflect % Optional,
      "io.monix" %% "monix" % V.monix % Optional,
      "io.monix" %% "monix-bio" % V.monix_bio % Optional,
      "org.scalamock" %% "scalamock" % V.scalamock % Test,
      "org.scalatest" %% "scalatest" % V.scalatest
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-core` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-core"))
  .dependsOn(
    `fundamentals-bio` % "test->compile;compile->compile",
    `fundamentals-platform` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %%% "izumi-reflect" % V.izumi_reflect % Optional
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `logstage-coreJVM` = `logstage-core`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `logstage-coreJS` = `logstage-core`.js
  .settings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % V.scala_java_time
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-rendering-circe` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-rendering-circe"))
  .dependsOn(
    `fundamentals-json-circe` % "test->compile;compile->compile",
    `logstage-core` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "jawn-parser" % V.jawn % Test,
      "io.circe" %%% "circe-parser" % V.circe % Test,
      "io.circe" %%% "circe-literal" % V.circe % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .jsSettings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    scalaJSLinkerConfig := { scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }
  )
lazy val `logstage-rendering-circeJVM` = `logstage-rendering-circe`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `logstage-rendering-circeJS` = `logstage-rendering-circe`.js
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-adapter-slf4j` = project.in(file("logstage/logstage-adapter-slf4j"))
  .dependsOn(
    `logstage-coreJVM` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.slf4j" % "slf4j-api" % V.slf4j
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    Compile / compileOrder := CompileOrder.Mixed,
    Test / compileOrder := CompileOrder.Mixed,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-sink-slf4j` = project.in(file("logstage/logstage-sink-slf4j"))
  .dependsOn(
    `logstage-coreJVM` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.slf4j" % "slf4j-api" % V.slf4j,
      "org.slf4j" % "slf4j-simple" % V.slf4j % Test
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .disablePlugins(AssemblyPlugin)

lazy val `microsite` = project.in(file("doc/microsite"))
  .dependsOn(
    `fundamentals-collectionsJVM` % "test->compile;compile->compile",
    `fundamentals-platformJVM` % "test->compile;compile->compile",
    `fundamentals-languageJVM` % "test->compile;compile->compile",
    `fundamentals-reflectionJVM` % "test->compile;compile->compile",
    `fundamentals-functionalJVM` % "test->compile;compile->compile",
    `fundamentals-bioJVM` % "test->compile;compile->compile",
    `fundamentals-json-circeJVM` % "test->compile;compile->compile",
    `fundamentals-orphansJVM` % "test->compile;compile->compile",
    `fundamentals-literalsJVM` % "test->compile;compile->compile",
    `distage-core-apiJVM` % "test->compile;compile->compile",
    `distage-core-proxy-cglib` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile;compile->compile",
    `distage-extension-config` % "test->compile;compile->compile",
    `distage-extension-plugins` % "test->compile;compile->compile",
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `distage-framework-api` % "test->compile;compile->compile",
    `distage-framework` % "test->compile;compile->compile",
    `distage-framework-docker` % "test->compile;compile->compile",
    `distage-testkit-core` % "test->compile;compile->compile",
    `distage-testkit-scalatest` % "test->compile;compile->compile",
    `logstage-coreJVM` % "test->compile;compile->compile",
    `logstage-rendering-circeJVM` % "test->compile;compile->compile",
    `logstage-adapter-slf4j` % "test->compile;compile->compile",
    `logstage-sink-slf4j` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats,
      "org.typelevel" %% "cats-effect" % V.cats_effect,
      "dev.zio" %% "zio" % V.zio excludeAll("dev.zio" %% "izumi-reflect"),
      "dev.zio" %% "zio-interop-cats" % V.zio_interop_cats excludeAll("dev.zio" %% "izumi-reflect"),
      "org.tpolecat" %% "doobie-core" % V.doobie,
      "org.tpolecat" %% "doobie-postgres" % V.doobie,
      "io.monix" %% "monix" % V.monix,
      "io.monix" %% "monix-bio" % V.monix_bio,
      "dev.zio" %% "izumi-reflect" % V.izumi_reflect
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions -= "-Wconf:any:error",
    coverageEnabled := false,
    publish / skip := true,
    DocKeys.prefix := {if (isSnapshot.value) {
                (s => s"latest/snapshot/$s")
              } else {
                identity
              }},
    previewFixedPort := Some(9999),
    git.remoteRepo := "git@github.com:7mind/izumi-microsite.git",
    Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mdocIn := baseDirectory.value / "src/main/tut",
    Paradox / sourceDirectory := mdocOut.value,
    mdocExtraArguments ++= Seq(
      " --no-link-hygiene"
    ),
    SitePlugin.autoImport.makeSite / mappings := {
                (SitePlugin.autoImport.makeSite / mappings)
                  .dependsOn(mdoc.toTask(" "))
                  .value
              },
    Paradox / version := version.value,
    ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox),
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    ScalaUnidoc / unidoc / unidocProjectFilter := inAggregates(`fundamentals-jvm`, transitive = true) || inAggregates(`distage-jvm`, transitive = true) || inAggregates(`logstage-jvm`, transitive = true),
    Paradox / paradoxMaterialTheme ~= {
                _.withCopyright("7mind.io")
                  .withRepository(uri("https://github.com/7mind/izumi"))
                //        .withColor("222", "434343")
              },
    ScalaUnidoc / siteSubdirName := DocKeys.prefix.value("api"),
    Paradox / siteSubdirName := DocKeys.prefix.value(""),
    paradoxProperties ++= Map(
                "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value("api")}",
                "scaladoc.base_url" -> s"/${DocKeys.prefix.value("api")}",
                "izumi.version" -> version.value,
              ),
    ghpagesCleanSite / excludeFilter :=
                new FileFilter {
                  def accept(f: File): Boolean = {
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("latest")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("distage")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("logstage")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("idealingua")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("bio")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("sbt")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("manifesto")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("pper")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("api")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("assets")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("lib")) ||
                      f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("search")) ||
                      f.toPath.startsWith((ghpagesRepository.value / "media").toPath) ||
                      (ghpagesRepository.value / "paradox.json").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / ".nojekyll").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / "index.html").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / "README.md").getCanonicalPath == f.getCanonicalPath
                  }
                }
  )
  .enablePlugins(ScalaUnidocPlugin, ParadoxSitePlugin, SitePlugin, GhpagesPlugin, ParadoxMaterialThemePlugin, PreprocessPlugin, MdocPlugin)
  .disablePlugins(ScoverageSbtPlugin, AssemblyPlugin)

lazy val `sbt-izumi-deps` = project.in(file("sbt-plugins/sbt-izumi-deps"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    crossScalaVersions := Seq(
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-name=${name.value}",
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    Test / testOptions += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.14") => Seq(
        "-Xsource:2.13",
        "-Ypartial-unification",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Xlint:adapted-args",
        "-Xlint:by-name-right-associative",
        "-Xlint:constant",
        "-Xlint:delayedinit-select",
        "-Xlint:doc-detached",
        "-Xlint:inaccessible",
        "-Xlint:infer-any",
        "-Xlint:missing-interpolator",
        "-Xlint:nullary-override",
        "-Xlint:nullary-unit",
        "-Xlint:option-implicit",
        "-Xlint:package-object-classes",
        "-Xlint:poly-implicit-overload",
        "-Xlint:private-shadow",
        "-Xlint:stars-align",
        "-Xlint:type-parameter-shadow",
        "-Xlint:unsound-match",
        "-opt-warnings:_",
        "-Ywarn-extra-implicit",
        "-Ywarn-unused:_",
        "-Ywarn-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, "2.13.6") => Seq(
        "-Xlint:_,-eta-sam,-multiarg-infix,-byname-implicit",
        if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
        "-Wconf:cat=optimizer:warning",
        "-Wconf:cat=other-match-analysis:error",
        "-Wconf:msg=kind-projector:silent",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Wmacros:after",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified",
        "-Wunused:-synthetics",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions += "-Wconf:msg=nowarn:silent",
    scalacOptions += "-Wconf:msg=parameter.value.x\\$4.in.anonymous.function.is.never.used:silent",
    scalacOptions += "-Wconf:msg=package.object.inheritance:silent",
    Compile / sbt.Keys.doc / scalacOptions -= "-Wconf:any:error",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
      s"-Xmacro-settings:is-ci=${insideCI.value}"
    ),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.14") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.6") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    sbtPlugin := true,
    withBuildInfo("izumi.sbt.deps", "Izumi")
  )
  .disablePlugins(ScoverageSbtPlugin, AssemblyPlugin)

lazy val `fundamentals` = (project in file(".agg/fundamentals-fundamentals"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-collectionsJS`,
    `fundamentals-platformJVM`,
    `fundamentals-platformJS`,
    `fundamentals-languageJVM`,
    `fundamentals-languageJS`,
    `fundamentals-reflectionJVM`,
    `fundamentals-reflectionJS`,
    `fundamentals-functionalJVM`,
    `fundamentals-functionalJS`,
    `fundamentals-bioJVM`,
    `fundamentals-bioJS`,
    `fundamentals-json-circeJVM`,
    `fundamentals-json-circeJS`,
    `fundamentals-orphansJVM`,
    `fundamentals-orphansJS`,
    `fundamentals-literalsJVM`,
    `fundamentals-literalsJS`
  )

lazy val `fundamentals-jvm` = (project in file(".agg/fundamentals-fundamentals-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-platformJVM`,
    `fundamentals-languageJVM`,
    `fundamentals-reflectionJVM`,
    `fundamentals-functionalJVM`,
    `fundamentals-bioJVM`,
    `fundamentals-json-circeJVM`,
    `fundamentals-orphansJVM`,
    `fundamentals-literalsJVM`
  )

lazy val `fundamentals-js` = (project in file(".agg/fundamentals-fundamentals-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJS`,
    `fundamentals-platformJS`,
    `fundamentals-languageJS`,
    `fundamentals-reflectionJS`,
    `fundamentals-functionalJS`,
    `fundamentals-bioJS`,
    `fundamentals-json-circeJS`,
    `fundamentals-orphansJS`,
    `fundamentals-literalsJS`
  )

lazy val `distage` = (project in file(".agg/distage-distage"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-core-apiJVM`,
    `distage-core-apiJS`,
    `distage-core-proxy-cglib`,
    `distage-coreJVM`,
    `distage-coreJS`,
    `distage-extension-config`,
    `distage-extension-plugins`,
    `distage-extension-logstageJVM`,
    `distage-extension-logstageJS`,
    `distage-framework-api`,
    `distage-framework`,
    `distage-framework-docker`,
    `distage-testkit-core`,
    `distage-testkit-scalatest`
  )

lazy val `distage-jvm` = (project in file(".agg/distage-distage-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-core-apiJVM`,
    `distage-core-proxy-cglib`,
    `distage-coreJVM`,
    `distage-extension-config`,
    `distage-extension-plugins`,
    `distage-extension-logstageJVM`,
    `distage-framework-api`,
    `distage-framework`,
    `distage-framework-docker`,
    `distage-testkit-core`,
    `distage-testkit-scalatest`
  )

lazy val `distage-js` = (project in file(".agg/distage-distage-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-core-apiJS`,
    `distage-core-proxy-cglib`,
    `distage-coreJS`,
    `distage-extension-config`,
    `distage-extension-plugins`,
    `distage-extension-logstageJS`,
    `distage-framework-api`,
    `distage-framework`,
    `distage-framework-docker`,
    `distage-testkit-core`,
    `distage-testkit-scalatest`
  )

lazy val `logstage` = (project in file(".agg/logstage-logstage"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-coreJVM`,
    `logstage-coreJS`,
    `logstage-rendering-circeJVM`,
    `logstage-rendering-circeJS`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `logstage-jvm` = (project in file(".agg/logstage-logstage-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-coreJVM`,
    `logstage-rendering-circeJVM`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `logstage-js` = (project in file(".agg/logstage-logstage-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-coreJS`,
    `logstage-rendering-circeJS`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `doc` = (project in file(".agg/doc-doc"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `doc-jvm` = (project in file(".agg/doc-doc-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `doc-js` = (project in file(".agg/doc-doc-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `sbt-plugins` = (project in file(".agg/sbt-plugins-sbt-plugins"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil,
    scalaVersion := "2.12.14"
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `sbt-plugins-jvm` = (project in file(".agg/sbt-plugins-sbt-plugins-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil,
    scalaVersion := "2.12.14"
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `sbt-plugins-js` = (project in file(".agg/sbt-plugins-sbt-plugins-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil,
    scalaVersion := "2.12.14"
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `izumi-jvm` = (project in file(".agg/.agg-jvm"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-jvm`,
    `distage-jvm`,
    `logstage-jvm`,
    `sbt-plugins-jvm`
  )

lazy val `izumi-js` = (project in file(".agg/.agg-js"))
  .settings(
    publish / skip := true,
    crossScalaVersions := Seq(
      "2.13.6",
      "2.12.14"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-js`,
    `distage-js`,
    `logstage-js`,
    `sbt-plugins-js`
  )

lazy val `izumi` = (project in file("."))
  .settings(
    publish / skip := true,
    ThisBuild / publishMavenStyle := true,
    ThisBuild / scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-target:jvm-1.8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:higherKinds",
      "-explaintypes"
    ),
    ThisBuild / javacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-source",
      "1.8",
      "-target",
      "1.8",
      "-deprecation",
      "-parameters",
      "-Xlint:all",
      "-XDignore.symbol.file"
    ),
    ThisBuild / scalacOptions ++= Seq(
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:git-repo-clean=${com.typesafe.sbt.SbtGit.GitKeys.gitUncommittedChanges.value}",
      s"-Xmacro-settings:git-branch=${com.typesafe.sbt.SbtGit.GitKeys.gitCurrentBranch.value}",
      s"-Xmacro-settings:git-described-version=${com.typesafe.sbt.SbtGit.GitKeys.gitDescribedVersion.value.getOrElse("")}",
      s"-Xmacro-settings:git-head-commit=${com.typesafe.sbt.SbtGit.GitKeys.gitHeadCommit.value.getOrElse("")}"
    ),
    crossScalaVersions := Nil,
    scalaVersion := "2.13.6",
    ThisBuild / organization := "io.7mind.izumi",
    sonatypeProfileName := "io.7mind",
    sonatypeSessionName := s"[sbt-sonatype] ${name.value} ${version.value} ${java.util.UUID.randomUUID}",
    ThisBuild / publishTo := 
    (if (!isSnapshot.value) {
        sonatypePublishToBundle.value
      } else {
        Some(Opts.resolver.sonatypeSnapshots)
    })
    ,
    ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "secrets" / "credentials.sonatype-nexus.properties"),
    ThisBuild / homepage := Some(url("https://izumi.7mind.io")),
    ThisBuild / licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
    ThisBuild / developers := List(
              Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
            ),
    ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git"))
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals`,
    `distage`,
    `logstage`,
    `sbt-plugins`
  )
