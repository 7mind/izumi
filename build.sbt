import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}



disablePlugins(AssemblyPlugin)

lazy val `fundamentals-collections` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-collections"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-collectionsJVM` = `fundamentals-collections`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-collectionsJS` = `fundamentals-collections`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-platform` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-platform"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-collections` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule,
    npmDependencies in Compile ++= Seq(
      (  "hash.js",  "1.1.7")
    )
  )
lazy val `fundamentals-platformJVM` = `fundamentals-platform`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-platformJS` = `fundamentals-platform`.js
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-functional` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-functional"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-functionalJVM` = `fundamentals-functional`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-functionalJS` = `fundamentals-functional`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-bio` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-bio"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-functional` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "org.typelevel" %%% "cats-core" % "2.0.0-RC1" % Optional,
      "org.typelevel" %%% "cats-effect" % "2.0.0-RC1" % Optional,
      "dev.zio" %%% "zio" % "1.0.0-RC11-1" % Optional
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-bioJVM` = `fundamentals-bio`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-bioJS` = `fundamentals-bio`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-typesafe-config` = project.in(file("fundamentals/fundamentals-typesafe-config"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platformJVM` % "test->compile;compile->compile",
    `fundamentals-functionalJVM` % "test->compile;compile->compile",
    `fundamentals-collectionsJVM` % "test->compile;compile->compile",
    `fundamentals-reflectionJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.typesafe" % "config" % "1.3.4",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val `fundamentals-reflection` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-reflection"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "io.suzaku" %%% "boopickle" % "1.3.1",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-reflectionJVM` = `fundamentals-reflection`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-reflectionJS` = `fundamentals-reflection`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-json-circe` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-json-circe"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "io.circe" %%% "circe-core" % "0.12.0-RC4",
      "io.circe" %%% "circe-parser" % "0.12.0-RC4",
      "io.circe" %%% "circe-literal" % "0.12.0-RC4",
      "io.circe" %%% "circe-generic-extras" % "0.12.0-RC4",
      "io.circe" %%% "circe-derivation" % "0.12.0-M5"
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-json-circeJVM` = `fundamentals-json-circe`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-json-circeJS` = `fundamentals-json-circe`.js
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "jawn-parser" % "0.14.2"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-model` = project.in(file("distage/distage-model"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platformJVM` % "test->compile;compile->compile",
    `fundamentals-functionalJVM` % "test->compile;compile->compile",
    `fundamentals-collectionsJVM` % "test->compile;compile->compile",
    `fundamentals-bioJVM` % "test->compile;compile->compile",
    `fundamentals-reflectionJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.typelevel" %% "cats-core" % "2.0.0-RC1" % Optional,
      "org.typelevel" %% "cats-effect" % "2.0.0-RC1" % Optional,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val `distage-proxy-cglib` = project.in(file("distage/distage-proxy-cglib"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platformJVM` % "test->compile;compile->compile",
    `fundamentals-functionalJVM` % "test->compile;compile->compile",
    `fundamentals-collectionsJVM` % "test->compile;compile->compile",
    `distage-model` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "cglib" % "cglib-nodep" % "3.3.0"
    )
  )

lazy val `distage-core` = project.in(file("distage/distage-core"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-model` % "test->compile;compile->compile",
    `distage-proxy-cglib` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "cglib" % "cglib-nodep" % "3.3.0"
    )
  )

lazy val `distage-config` = project.in(file("distage/distage-config"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-model` % "test->compile;compile->compile",
    `fundamentals-typesafe-config` % "test->compile;compile->compile",
    `distage-core` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.typesafe" % "config" % "1.3.4"
    )
  )

lazy val `distage-roles-api` = project.in(file("distage/distage-roles-api"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-model` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `distage-plugins` = project.in(file("distage/distage-plugins"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-model` % "test->compile;compile->compile",
    `distage-core` % "test->compile,test",
    `distage-config` % "test->compile",
    `logstage-coreJVM` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "io.github.classgraph" % "classgraph" % "4.8.47"
    )
  )

lazy val `distage-roles` = project.in(file("distage/distage-roles"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-roles-api` % "test->compile;compile->compile",
    `logstage-di` % "test->compile;compile->compile",
    `logstage-adapter-slf4j` % "test->compile;compile->compile",
    `logstage-rendering-circeJVM` % "test->compile;compile->compile",
    `distage-core` % "test->test;compile->compile",
    `distage-plugins` % "test->test;compile->compile",
    `distage-config` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.typelevel" %% "cats-core" % "2.0.0-RC1" % Optional,
      "org.typelevel" %% "cats-effect" % "2.0.0-RC1" % Optional,
      "dev.zio" %% "zio" % "1.0.0-RC11-1" % Optional
    )
  )

lazy val `distage-static` = project.in(file("distage/distage-static"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-core` % "test->test;compile->compile",
    `distage-roles` % "test->compile,test"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.chuusai" %% "shapeless" % "2.3.3"
    )
  )

lazy val `distage-testkit` = project.in(file("distage/distage-testkit"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `distage-config` % "test->compile;compile->compile",
    `distage-roles` % "test->compile;compile->compile",
    `logstage-di` % "test->compile;compile->compile",
    `distage-core` % "test->test;compile->compile",
    `distage-plugins` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.scalatest" %% "scalatest" % "3.0.8",
      "org.typelevel" %% "cats-core" % "2.0.0-RC1" % Test,
      "org.typelevel" %% "cats-effect" % "2.0.0-RC1" % Test,
      "dev.zio" %% "zio" % "1.0.0-RC11-1" % Test
    )
  )

lazy val `logstage-api` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-api"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-reflection` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3"
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `logstage-apiJVM` = `logstage-api`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `logstage-apiJS` = `logstage-api`.js
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-core` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-core"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-bio` % "test->compile;compile->compile",
    `logstage-api` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "org.typelevel" %%% "cats-core" % "2.0.0-RC1" % Optional,
      "dev.zio" %%% "zio" % "1.0.0-RC11-1" % Optional,
      "org.typelevel" %%% "cats-core" % "2.0.0-RC1" % Test,
      "org.typelevel" %%% "cats-effect" % "2.0.0-RC1" % Test,
      "dev.zio" %%% "zio" % "1.0.0-RC11-1" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `logstage-coreJVM` = `logstage-core`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `logstage-coreJS` = `logstage-core`.js
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-rendering-circe` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-rendering-circe"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-json-circe` % "test->compile;compile->compile",
    `logstage-core` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `logstage-rendering-circeJVM` = `logstage-rendering-circe`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `logstage-rendering-circeJS` = `logstage-rendering-circe`.js
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-di` = project.in(file("logstage/logstage-di"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `logstage-config` % "test->compile;compile->compile",
    `distage-config` % "test->compile;compile->compile",
    `distage-model` % "test->compile;compile->compile",
    `distage-core` % "test->compile",
    `logstage-coreJVM` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `logstage-config` = project.in(file("logstage/logstage-config"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-typesafe-config` % "test->compile;compile->compile",
    `logstage-coreJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `logstage-adapter-slf4j` = project.in(file("logstage/logstage-adapter-slf4j"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    compileOrder in Compile := CompileOrder.Mixed,
    compileOrder in Test := CompileOrder.Mixed,
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `logstage-coreJVM` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.slf4j" % "slf4j-api" % "1.7.28"
    )
  )

lazy val `logstage-sink-slf4j` = project.in(file("logstage/logstage-sink-slf4j"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `logstage-apiJVM` % "test->compile;compile->compile",
    `logstage-coreJVM` % "test->compile,test"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.slf4j" % "slf4j-api" % "1.7.28",
      "org.slf4j" % "slf4j-simple" % "1.7.28" % Test
    )
  )

lazy val `idealingua-v1-model` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("idealingua-v1/idealingua-v1-model"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `idealingua-v1-modelJVM` = `idealingua-v1-model`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `idealingua-v1-modelJS` = `idealingua-v1-model`.js
  .disablePlugins(AssemblyPlugin)

lazy val `idealingua-v1-core` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("idealingua-v1/idealingua-v1-core"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile",
    `idealingua-v1-model` % "test->compile;compile->compile",
    `fundamentals-reflection` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "com.lihaoyi" %%% "fastparse" % "2.1.3"
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `idealingua-v1-coreJVM` = `idealingua-v1-core`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `idealingua-v1-coreJS` = `idealingua-v1-core`.js
  .disablePlugins(AssemblyPlugin)

lazy val `idealingua-v1-runtime-rpc-scala` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("idealingua-v1/idealingua-v1-runtime-rpc-scala"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile",
    `fundamentals-bio` % "test->compile;compile->compile",
    `fundamentals-json-circe` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "org.typelevel" %%% "cats-core" % "2.0.0-RC1",
      "org.typelevel" %%% "cats-effect" % "2.0.0-RC1",
      "dev.zio" %%% "zio" % "1.0.0-RC11-1",
      "dev.zio" %%% "zio-interop-cats" % "2.0.0.0-RC2"
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `idealingua-v1-runtime-rpc-scalaJVM` = `idealingua-v1-runtime-rpc-scala`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `idealingua-v1-runtime-rpc-scalaJS` = `idealingua-v1-runtime-rpc-scala`.js
  .disablePlugins(AssemblyPlugin)

lazy val `idealingua-v1-runtime-rpc-http4s` = project.in(file("idealingua-v1/idealingua-v1-runtime-rpc-http4s"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `idealingua-v1-runtime-rpc-scalaJVM` % "test->compile;compile->compile",
    `logstage-coreJVM` % "test->compile;compile->compile",
    `logstage-adapter-slf4j` % "test->compile;compile->compile",
    `idealingua-v1-test-defs` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.http4s" %% "http4s-dsl" % "0.21.0-M4",
      "org.http4s" %% "http4s-circe" % "0.21.0-M4",
      "org.http4s" %% "http4s-blaze-server" % "0.21.0-M4",
      "org.http4s" %% "http4s-blaze-client" % "0.21.0-M4",
      "org.asynchttpclient" % "async-http-client" % "2.10.1"
    )
  )

lazy val `idealingua-v1-transpilers` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("idealingua-v1/idealingua-v1-transpilers"))
  .settings(
    organization := "io.7mind.izumi",
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile",
    `fundamentals-json-circe` % "test->compile;compile->compile",
    `idealingua-v1-core` % "test->compile;compile->compile",
    `idealingua-v1-runtime-rpc-scala` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test,
      "org.scala-lang.modules" %%% "scala-xml" % "1.2.0",
      "org.scalameta" %%% "scalameta" % "4.2.3"
    )
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    fork in Test := true
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `idealingua-v1-transpilersJVM` = `idealingua-v1-transpilers`.jvm
    .dependsOn(
      `idealingua-v1-test-defs` % "test->compile",
      `idealingua-v1-runtime-rpc-typescript` % "test->compile",
      `idealingua-v1-runtime-rpc-go` % "test->compile",
      `idealingua-v1-runtime-rpc-csharp` % "test->compile"
    )
  .disablePlugins(AssemblyPlugin)
lazy val `idealingua-v1-transpilersJS` = `idealingua-v1-transpilers`.js
  .disablePlugins(AssemblyPlugin)

lazy val `idealingua-v1-test-defs` = project.in(file("idealingua-v1/idealingua-v1-test-defs"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `idealingua-v1-runtime-rpc-scalaJVM` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `idealingua-v1-runtime-rpc-typescript` = project.in(file("idealingua-v1/idealingua-v1-runtime-rpc-typescript"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `idealingua-v1-runtime-rpc-go` = project.in(file("idealingua-v1/idealingua-v1-runtime-rpc-go"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `idealingua-v1-runtime-rpc-csharp` = project.in(file("idealingua-v1/idealingua-v1-runtime-rpc-csharp"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `idealingua-v1-compiler` = project.in(file("idealingua-v1/idealingua-v1-compiler"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    mainClass in assembly := Some("izumi.idealingua.compiler.CommandlineIDLCompiler"),
    assemblyMergeStrategy in assembly := {
          // FIXME: workaround for https://github.com/zio/interop-cats/issues/16
          case path if path.contains("zio/BuildInfo$.class") =>
            MergeStrategy.last
          case p =>
            (assemblyMergeStrategy in assembly).value(p)
    },
    artifact in (Compile, assembly) := {
          val art = (artifact in(Compile, assembly)).value
          art.withClassifier(Some("assembly"))
    },
    addArtifact(artifact in(Compile, assembly), assembly),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `idealingua-v1-transpilersJVM` % "test->compile;compile->compile",
    `idealingua-v1-runtime-rpc-scalaJVM` % "test->compile;compile->compile",
    `idealingua-v1-runtime-rpc-typescript` % "test->compile;compile->compile",
    `idealingua-v1-runtime-rpc-go` % "test->compile;compile->compile",
    `idealingua-v1-runtime-rpc-csharp` % "test->compile;compile->compile",
    `idealingua-v1-test-defs` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "com.typesafe" % "config" % "1.3.4"
    )
  )

lazy val `microsite` = project.in(file("doc/microsite"))
  .enablePlugins(ScalaUnidocPlugin, ParadoxSitePlugin, SitePlugin, GhpagesPlugin, ParadoxMaterialThemePlugin, PreprocessPlugin, MdocPlugin)
  .disablePlugins(ScoverageSbtPlugin, AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.8"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    crossScalaVersions := Seq(
      "2.12.8"
    ),
    scalaVersion := crossScalaVersions.value.head,
    coverageEnabled := false,
    skip in publish := true,
    DocKeys.prefix := {if (isSnapshot.value) {
                "latest/snapshot"
              } else {
                "latest/release"
              }},
    previewFixedPort := Some(9999),
    git.remoteRepo := "git@github.com:7mind/izumi-microsite.git",
    classLoaderLayeringStrategy in Compile := ClassLoaderLayeringStrategy.Flat,
    mdocIn := baseDirectory.value / "src/main/tut",
    sourceDirectory in Paradox := mdocOut.value,
    mdocExtraArguments ++= Seq(
      " --no-link-hygiene"
    ),
    mappings in SitePlugin.autoImport.makeSite := {
                (mappings in SitePlugin.autoImport.makeSite)
                  .dependsOn(mdoc.toTask(" "))
                  .value
              },
    version in Paradox := version.value,
    ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox),
    addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    unidocProjectFilter in(ScalaUnidoc, unidoc) := inAggregates(`izumi-jvm`, transitive=true),
    paradoxMaterialTheme in Paradox ~= {
                _.withCopyright("7mind.io")
                  .withRepository(uri("https://github.com/7mind/izumi"))
                //        .withColor("222", "434343")
              },
    siteSubdirName in ScalaUnidoc := s"${DocKeys.prefix.value}/api",
    siteSubdirName in Paradox := s"${DocKeys.prefix.value}/doc",
    paradoxProperties ++= Map(
                "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/com/github/pshirshov/",
                "scaladoc.base_url" -> s"/${DocKeys.prefix.value}/api/",
                "izumi.version" -> version.value,
              ),
    excludeFilter in ghpagesCleanSite :=
                new FileFilter {
                  def accept(f: File): Boolean = {
                    (f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("latest")) && !f.toPath.startsWith(ghpagesRepository.value.toPath.resolve(DocKeys.prefix.value))) ||
                      (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / ".nojekyll").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / "index.html").getCanonicalPath == f.getCanonicalPath ||
                      (ghpagesRepository.value / "README.md").getCanonicalPath == f.getCanonicalPath ||
                      f.toPath.startsWith((ghpagesRepository.value / "media").toPath) ||
                      f.toPath.startsWith((ghpagesRepository.value / "v0.5.50-SNAPSHOT").toPath)
                  }
                },
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .dependsOn(
    `fundamentals-collectionsJVM` % "test->compile;compile->compile",
    `fundamentals-platformJVM` % "test->compile;compile->compile",
    `fundamentals-functionalJVM` % "test->compile;compile->compile",
    `fundamentals-bioJVM` % "test->compile;compile->compile",
    `fundamentals-typesafe-config` % "test->compile;compile->compile",
    `fundamentals-reflectionJVM` % "test->compile;compile->compile",
    `fundamentals-json-circeJVM` % "test->compile;compile->compile",
    `distage-model` % "test->compile;compile->compile",
    `distage-proxy-cglib` % "test->compile;compile->compile",
    `distage-core` % "test->compile;compile->compile",
    `distage-config` % "test->compile;compile->compile",
    `distage-roles-api` % "test->compile;compile->compile",
    `distage-plugins` % "test->compile;compile->compile",
    `distage-roles` % "test->compile;compile->compile",
    `distage-static` % "test->compile;compile->compile",
    `distage-testkit` % "test->compile;compile->compile",
    `logstage-apiJVM` % "test->compile;compile->compile",
    `logstage-coreJVM` % "test->compile;compile->compile",
    `logstage-rendering-circeJVM` % "test->compile;compile->compile",
    `logstage-di` % "test->compile;compile->compile",
    `logstage-config` % "test->compile;compile->compile",
    `logstage-adapter-slf4j` % "test->compile;compile->compile",
    `logstage-sink-slf4j` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.typelevel" %% "cats-core" % "2.0.0-RC1",
      "org.typelevel" %% "cats-effect" % "2.0.0-RC1",
      "dev.zio" %% "zio" % "1.0.0-RC11-1",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC2",
      "org.http4s" %% "http4s-dsl" % "0.21.0-M4",
      "org.http4s" %% "http4s-circe" % "0.21.0-M4",
      "org.http4s" %% "http4s-blaze-server" % "0.21.0-M4",
      "org.http4s" %% "http4s-blaze-client" % "0.21.0-M4"
    )
  )

lazy val `sbt-izumi-deps` = project.in(file("sbt-plugins/sbt-izumi-deps"))
  .disablePlugins(AssemblyPlugin)
  .settings(
    organization := "io.7mind.izumi",
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    publishArtifact in (Test, packageBin) := true,
    publishArtifact in (Test, packageDoc) := false,
    publishArtifact in (Test, packageSrc) := true,
    sbtPlugin := true,
    withBuildInfo("izumi.sbt.deps", "Izumi"),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.9") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.12.8") => Seq(
        "-Ypartial-unification",
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        "8",
        "-opt-warnings:_",
        "-Ywarn-unused:_",
        "-Yno-adapted-args",
        "-explaintypes",
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
        "-Ywarn-value-discard"
      )
      case (_, "2.13.0") => Seq(
        "-Xsource:2.13",
        "-explaintypes",
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wvalue-discard",
        "-Wunused:_",
        "-Xlint:_"
      )
      case (_, _) => Seq.empty
    } }
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `fundamentals` = (project in file("fundamentals"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-collectionsJS`,
    `fundamentals-platformJVM`,
    `fundamentals-platformJS`,
    `fundamentals-functionalJVM`,
    `fundamentals-functionalJS`,
    `fundamentals-bioJVM`,
    `fundamentals-bioJS`,
    `fundamentals-typesafe-config`,
    `fundamentals-reflectionJVM`,
    `fundamentals-reflectionJS`,
    `fundamentals-json-circeJVM`,
    `fundamentals-json-circeJS`
  )

lazy val `fundamentals-jvm` = (project in file(".agg/fundamentals-fundamentals-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-platformJVM`,
    `fundamentals-functionalJVM`,
    `fundamentals-bioJVM`,
    `fundamentals-typesafe-config`,
    `fundamentals-reflectionJVM`,
    `fundamentals-json-circeJVM`
  )

lazy val `fundamentals-js` = (project in file(".agg/fundamentals-fundamentals-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJS`,
    `fundamentals-platformJS`,
    `fundamentals-functionalJS`,
    `fundamentals-bioJS`,
    `fundamentals-typesafe-config`,
    `fundamentals-reflectionJS`,
    `fundamentals-json-circeJS`
  )

lazy val `distage` = (project in file("distage"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-model`,
    `distage-proxy-cglib`,
    `distage-core`,
    `distage-config`,
    `distage-roles-api`,
    `distage-plugins`,
    `distage-roles`,
    `distage-static`,
    `distage-testkit`
  )

lazy val `distage-jvm` = (project in file(".agg/distage-distage-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-model`,
    `distage-proxy-cglib`,
    `distage-core`,
    `distage-config`,
    `distage-roles-api`,
    `distage-plugins`,
    `distage-roles`,
    `distage-static`,
    `distage-testkit`
  )

lazy val `distage-js` = (project in file(".agg/distage-distage-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `distage-model`,
    `distage-proxy-cglib`,
    `distage-core`,
    `distage-config`,
    `distage-roles-api`,
    `distage-plugins`,
    `distage-roles`,
    `distage-static`,
    `distage-testkit`
  )

lazy val `logstage` = (project in file("logstage"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-apiJVM`,
    `logstage-apiJS`,
    `logstage-coreJVM`,
    `logstage-coreJS`,
    `logstage-rendering-circeJVM`,
    `logstage-rendering-circeJS`,
    `logstage-di`,
    `logstage-config`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `logstage-jvm` = (project in file(".agg/logstage-logstage-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-apiJVM`,
    `logstage-coreJVM`,
    `logstage-rendering-circeJVM`,
    `logstage-di`,
    `logstage-config`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `logstage-js` = (project in file(".agg/logstage-logstage-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `logstage-apiJS`,
    `logstage-coreJS`,
    `logstage-rendering-circeJS`,
    `logstage-di`,
    `logstage-config`,
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `idealingua` = (project in file("idealingua-v1"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `idealingua-v1-modelJVM`,
    `idealingua-v1-modelJS`,
    `idealingua-v1-coreJVM`,
    `idealingua-v1-coreJS`,
    `idealingua-v1-runtime-rpc-scalaJVM`,
    `idealingua-v1-runtime-rpc-scalaJS`,
    `idealingua-v1-runtime-rpc-http4s`,
    `idealingua-v1-transpilersJVM`,
    `idealingua-v1-transpilersJS`,
    `idealingua-v1-test-defs`,
    `idealingua-v1-runtime-rpc-typescript`,
    `idealingua-v1-runtime-rpc-go`,
    `idealingua-v1-runtime-rpc-csharp`,
    `idealingua-v1-compiler`
  )

lazy val `idealingua-jvm` = (project in file(".agg/idealingua-v1-idealingua-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `idealingua-v1-modelJVM`,
    `idealingua-v1-coreJVM`,
    `idealingua-v1-runtime-rpc-scalaJVM`,
    `idealingua-v1-runtime-rpc-http4s`,
    `idealingua-v1-transpilersJVM`,
    `idealingua-v1-test-defs`,
    `idealingua-v1-runtime-rpc-typescript`,
    `idealingua-v1-runtime-rpc-go`,
    `idealingua-v1-runtime-rpc-csharp`,
    `idealingua-v1-compiler`
  )

lazy val `idealingua-js` = (project in file(".agg/idealingua-v1-idealingua-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `idealingua-v1-modelJS`,
    `idealingua-v1-coreJS`,
    `idealingua-v1-runtime-rpc-scalaJS`,
    `idealingua-v1-runtime-rpc-http4s`,
    `idealingua-v1-transpilersJS`,
    `idealingua-v1-test-defs`,
    `idealingua-v1-runtime-rpc-typescript`,
    `idealingua-v1-runtime-rpc-go`,
    `idealingua-v1-runtime-rpc-csharp`,
    `idealingua-v1-compiler`
  )

lazy val `doc` = (project in file("doc"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.8"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `doc-jvm` = (project in file(".agg/doc-doc-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.8"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `doc-js` = (project in file(".agg/doc-doc-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.8"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `sbt-plugins` = (project in file("sbt-plugins"))
  .settings(
    skip in publish := true
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `sbt-plugins-jvm` = (project in file(".agg/sbt-plugins-sbt-plugins-jvm"))
  .settings(
    skip in publish := true
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `sbt-plugins-js` = (project in file(".agg/sbt-plugins-sbt-plugins-js"))
  .settings(
    skip in publish := true
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `izumi` = (project in file("."))
  .settings(
    skip in publish := true,
    publishMavenStyle in ThisBuild := true,
    scalacOptions in ThisBuild ++= Seq(
      "-encoding",
      "UTF-8",
      "-target:jvm-1.8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:higherKinds"
    ),
    javacOptions in ThisBuild ++= Seq(
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
    crossScalaVersions := Nil,
    scalaVersion := "2.12.9",
    organization in ThisBuild := "io.7mind.izumi",
    publishTo in ThisBuild := 
    (if (!isSnapshot.value) {
        sonatypePublishToBundle.value
      } else {
        Some(Opts.resolver.sonatypeSnapshots)
    })
    ,
    credentials in ThisBuild += Credentials(file(".secrets/credentials.sonatype-nexus.properties")),
    homepage in ThisBuild := Some(url("https://izumi.7mind.io")),
    licenses in ThisBuild := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
    developers in ThisBuild := List(
              Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
            ),
    scmInfo in ThisBuild := Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git")),
    scalacOptions in ThisBuild += s"-Xmacro-settings:product-version=${version.value}",
    scalacOptions in ThisBuild += s"-Xmacro-settings:product-group=${organization.value}",
    scalacOptions in ThisBuild += s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
    scalacOptions in ThisBuild += s"-Xmacro-settings:scala-version=${scalaVersion.value}",
    scalacOptions in ThisBuild += """-Xmacro-settings:scalatest-version=3.0.8""",
    scalacOptions in ThisBuild += """-Xmacro-settings:scala-versions=2.12.9:2.13.0""",
    scalacOptions in ThisBuild ++= Seq("-Ybackend-parallelism", math.max(1, sys.runtime.availableProcessors() - 1).toString)
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals`,
    `distage`,
    `logstage`,
    `idealingua`
  )

lazy val `izumi-jvm` = (project in file(".agg/.agg-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-jvm`,
    `distage-jvm`,
    `logstage-jvm`,
    `idealingua-jvm`
  )

lazy val `izumi-js` = (project in file(".agg/.agg-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.9",
      "2.13.0"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-js`,
    `distage-js`,
    `logstage-js`,
    `idealingua-js`
  )