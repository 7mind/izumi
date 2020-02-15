import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}



enablePlugins(SbtgenVerificationPlugin)

disablePlugins(AssemblyPlugin)

lazy val `fundamentals-collections` = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-collections"))
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
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1",
      "2.11.12"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
  .nativeSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.11.12"
    ),
    coverageEnabled := false,
    test := {}
  )
lazy val `fundamentals-collectionsJVM` = `fundamentals-collections`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-collectionsJS` = `fundamentals-collections`.js
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-collectionsNative` = `fundamentals-collections`.native

lazy val `fundamentals-platform` = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-platform"))
  .dependsOn(
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
    unmanagedSourceDirectories in Compile := (unmanagedSourceDirectories in Compile).value.flatMap {
      dir =>
       Seq(dir, file(dir.getPath + (CrossVersion.partialVersion(scalaVersion.value) match {
         case Some((2, 11)) => "_2.11"
         case Some((2, 12)) => "_2.12"
         case Some((2, 13)) => "_2.13"
         case _             => "_3.0"
       })))
    },
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1",
      "2.11.12"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule,
    npmDependencies in Test ++= Seq(
      (  "hash.js",  "1.1.7")
    )
  )
  .nativeSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.11.12"
    ),
    coverageEnabled := false,
    test := {}
  )
lazy val `fundamentals-platformJVM` = `fundamentals-platform`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-platformJS` = `fundamentals-platform`.js
  .enablePlugins(ScalaJSBundlerPlugin)
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-platformNative` = `fundamentals-platform`.native

lazy val `fundamentals-functional` = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-functional"))
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
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1",
      "2.11.12"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
  .nativeSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.11.12"
    ),
    coverageEnabled := false,
    test := {}
  )
lazy val `fundamentals-functionalJVM` = `fundamentals-functional`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-functionalJS` = `fundamentals-functional`.js
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-functionalNative` = `fundamentals-functional`.native

lazy val `fundamentals-thirdparty-boopickle-shaded` = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-thirdparty-boopickle-shaded"))
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
    unmanagedSourceDirectories in Compile := (unmanagedSourceDirectories in Compile).value.flatMap {
      dir =>
       Seq(dir, file(dir.getPath + (CrossVersion.partialVersion(scalaVersion.value) match {
         case Some((2, 11)) => "_2.11"
         case Some((2, 12)) => "_2.12"
         case Some((2, 13)) => "_2.13"
         case _             => "_3.0"
       })))
    },
    scalacOptions in Compile --= Seq("-Ywarn-value-discard","-Ywarn-unused:_", "-Wvalue-discard", "-Wunused:_"),
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1",
      "2.11.12"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
  .nativeSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.11.12"
    ),
    coverageEnabled := false,
    test := {}
  )
lazy val `fundamentals-thirdparty-boopickle-shadedJVM` = `fundamentals-thirdparty-boopickle-shaded`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-thirdparty-boopickle-shadedJS` = `fundamentals-thirdparty-boopickle-shaded`.js
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-thirdparty-boopickle-shadedNative` = `fundamentals-thirdparty-boopickle-shaded`.native

lazy val `fundamentals-reflection` = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-reflection"))
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-thirdparty-boopickle-shaded` % "test->compile;compile->compile"
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
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1",
      "2.11.12"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
  .nativeSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.11.12"
    ),
    coverageEnabled := false,
    test := {}
  )
lazy val `fundamentals-reflectionJVM` = `fundamentals-reflection`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-reflectionJS` = `fundamentals-reflection`.js
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-reflectionNative` = `fundamentals-reflection`.native

lazy val `fundamentals-bio` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-bio"))
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-bioJVM` = `fundamentals-bio`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-bioJS` = `fundamentals-bio`.js
  .disablePlugins(AssemblyPlugin)

lazy val `fundamentals-json-circe` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("fundamentals/fundamentals-json-circe"))
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-parser" % V.circe,
      "io.circe" %%% "circe-literal" % V.circe,
      "io.circe" %%% "circe-generic-extras" % V.circe_generic_extras,
      "io.circe" %%% "circe-derivation" % V.circe_derivation
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `fundamentals-json-circeJVM` = `fundamentals-json-circe`.jvm
  .disablePlugins(AssemblyPlugin)
lazy val `fundamentals-json-circeJS` = `fundamentals-json-circe`.js
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "jawn-parser" % V.jawn
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-core-api` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("distage/distage-core-api"))
  .dependsOn(
    `fundamentals-platform` % "test->compile;compile->compile",
    `fundamentals-collections` % "test->compile;compile->compile",
    `fundamentals-functional` % "test->compile;compile->compile",
    `fundamentals-bio` % "test->compile;compile->compile",
    `fundamentals-reflection` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
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
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
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
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
  )
lazy val `distage-coreJVM` = `distage-core`.jvm
  .dependsOn(
    `distage-core-proxy-cglib` % "test->compile;compile->compile"
  )
  .disablePlugins(AssemblyPlugin)
lazy val `distage-coreJS` = `distage-core`.js
  .disablePlugins(AssemblyPlugin)

lazy val `distage-extension-config` = project.in(file("distage/distage-extension-config"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile,test"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-derivation" % V.circe_derivation,
      "io.circe" %% "circe-config" % V.circe_config,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-extension-plugins` = project.in(file("distage/distage-extension-plugins"))
  .dependsOn(
    `distage-core-apiJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile,test",
    `distage-extension-config` % "test->compile",
    `logstage-coreJVM` % "test->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "io.github.classgraph" % "classgraph" % V.classgraph
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
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
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
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
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-framework` = project.in(file("distage/distage-framework"))
  .dependsOn(
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `logstage-rendering-circeJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->test;compile->compile",
    `distage-framework-api` % "test->test;compile->compile",
    `distage-extension-plugins` % "test->test;compile->compile",
    `distage-extension-config` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %% "zio" % V.zio % Optional,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
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
      "dev.zio" %% "zio" % V.zio % Test,
      "com.github.docker-java" % "docker-java" % V.docker_java
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-testkit-core` = project.in(file("distage/distage-testkit-core"))
  .dependsOn(
    `distage-extension-config` % "test->compile;compile->compile",
    `distage-framework` % "test->compile;compile->compile",
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %% "zio" % V.zio % Optional
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-testkit-scalatest` = project.in(file("distage/distage-testkit-scalatest"))
  .dependsOn(
    `distage-testkit-core` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->test;compile->compile",
    `distage-extension-plugins` % "test->test;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %% "zio" % V.zio % Optional,
      "org.scalatest" %% "scalatest" % V.scalatest
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `distage-testkit-legacy` = project.in(file("distage/distage-testkit-legacy"))
  .dependsOn(
    `distage-extension-config` % "test->compile;compile->compile",
    `distage-framework` % "test->compile;compile->compile",
    `distage-extension-logstageJVM` % "test->compile;compile->compile",
    `distage-coreJVM` % "test->compile;compile->compile",
    `distage-testkit-core` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.typelevel" %% "cats-core" % V.cats % Optional,
      "org.typelevel" %% "cats-effect" % V.cats_effect % Optional,
      "dev.zio" %% "zio" % V.zio % Optional,
      "org.scalatest" %% "scalatest" % V.scalatest
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-core` = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("logstage/logstage-core"))
  .dependsOn(
    `fundamentals-bio` % "test->compile;compile->compile",
    `fundamentals-reflection` % "test->compile;compile->compile"
  )
  .settings(
    libraryDependencies ++= Seq(
      compilerPlugin("org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.full),
      "org.scala-lang.modules" %%% "scala-collection-compat" % V.collection_compat,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      "org.typelevel" %%% "cats-core" % V.cats % Optional,
      "dev.zio" %%% "zio" % V.zio % Optional,
      "org.typelevel" %%% "cats-core" % V.cats % Test,
      "org.typelevel" %%% "cats-effect" % V.cats_effect % Test,
      "dev.zio" %%% "zio" % V.zio % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
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
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .settings(
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } }
  )
  .jvmSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .jsSettings(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    coverageEnabled := false,
    scalaJSModuleKind := ModuleKind.CommonJSModule
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
    organization := "io.7mind.izumi",
    compileOrder in Compile := CompileOrder.Mixed,
    compileOrder in Test := CompileOrder.Mixed,
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `logstage-sink-slf4j` = project.in(file("logstage/logstage-sink-slf4j"))
  .dependsOn(
    `logstage-coreJVM` % "test->compile;compile->compile",
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
    organization := "io.7mind.izumi",
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
  )
  .disablePlugins(AssemblyPlugin)

lazy val `microsite` = project.in(file("doc/microsite"))
  .dependsOn(
    `fundamentals-bioJVM` % "test->compile;compile->compile",
    `fundamentals-json-circeJVM` % "test->compile;compile->compile",
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
    `distage-testkit-legacy` % "test->compile;compile->compile",
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
      "dev.zio" %% "zio" % V.zio,
      "dev.zio" %% "zio-interop-cats" % V.zio_interop_cats,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-blaze-server" % V.http4s,
      "org.http4s" %% "http4s-blaze-client" % V.http4s
    )
  )
  .settings(
    organization := "io.7mind.izumi",
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
    unidocProjectFilter in(ScalaUnidoc, unidoc) := inAggregates(`fundamentals-jvm`, transitive = true) || inAggregates(`distage-jvm`, transitive = true) || inAggregates(`logstage-jvm`, transitive = true),
    paradoxMaterialTheme in Paradox ~= {
                _.withCopyright("7mind.io")
                  .withRepository(uri("https://github.com/7mind/izumi"))
                //        .withColor("222", "434343")
              },
    siteSubdirName in ScalaUnidoc := s"${DocKeys.prefix.value}/api",
    siteSubdirName in Paradox := s"${DocKeys.prefix.value}/doc",
    paradoxProperties ++= Map(
                "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/",
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
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    )
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
    organization := "io.7mind.izumi",
    sbtPlugin := true,
    withBuildInfo("izumi.sbt.deps", "Izumi"),
    scalacOptions ++= Seq(
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}"
    ),
    testOptions in Test += Tests.Argument("-oDF"),
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (_, "2.12.10") => Seq(
        "-Xsource:2.13",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Ypartial-unification",
        "-Yno-adapted-args",
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
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, "2.13.1") => Seq(
        "-Xlint:_,-eta-sam",
        "-Ybackend-parallelism",
        math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,
        "-Wdead-code",
        "-Wextra-implicit",
        "-Wnumeric-widen",
        "-Woctal-literal",
        "-Wunused:_",
        "-Wvalue-discard",
        "-Ycache-plugin-class-loader:always",
        "-Ycache-macro-class-loader:last-modified"
      )
      case (_, _) => Seq.empty
    } },
    scalacOptions ++= { (isSnapshot.value, scalaVersion.value) match {
      case (false, "2.12.10") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (false, "2.13.1") => Seq(
        "-opt:l:inline",
        "-opt-inline-from:izumi.**"
      )
      case (_, _) => Seq.empty
    } },
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq(
      "2.12.10"
    ),
    coverageEnabled := false
  )
  .disablePlugins(ScoverageSbtPlugin, AssemblyPlugin)

lazy val `fundamentals-cross` = (project in file(".agg/fundamentals-fundamentals-cross"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Nil
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-collectionsJS`,
    `fundamentals-collectionsNative`,
    `fundamentals-platformJVM`,
    `fundamentals-platformJS`,
    `fundamentals-platformNative`,
    `fundamentals-functionalJVM`,
    `fundamentals-functionalJS`,
    `fundamentals-functionalNative`,
    `fundamentals-thirdparty-boopickle-shadedJVM`,
    `fundamentals-thirdparty-boopickle-shadedJS`,
    `fundamentals-thirdparty-boopickle-shadedNative`,
    `fundamentals-reflectionJVM`,
    `fundamentals-reflectionJS`,
    `fundamentals-reflectionNative`
  )

lazy val `fundamentals-cross-jvm` = (project in file(".agg/fundamentals-fundamentals-cross-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Nil
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJVM`,
    `fundamentals-platformJVM`,
    `fundamentals-functionalJVM`,
    `fundamentals-thirdparty-boopickle-shadedJVM`,
    `fundamentals-reflectionJVM`
  )

lazy val `fundamentals-cross-js` = (project in file(".agg/fundamentals-fundamentals-cross-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Nil
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-collectionsJS`,
    `fundamentals-platformJS`,
    `fundamentals-functionalJS`,
    `fundamentals-thirdparty-boopickle-shadedJS`,
    `fundamentals-reflectionJS`
  )

lazy val `fundamentals-cross-native` = (project in file(".agg/fundamentals-fundamentals-cross-native"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Nil
  )
  .aggregate(
    `fundamentals-collectionsNative`,
    `fundamentals-platformNative`,
    `fundamentals-functionalNative`,
    `fundamentals-thirdparty-boopickle-shadedNative`,
    `fundamentals-reflectionNative`
  )

lazy val `fundamentals` = (project in file(".agg/fundamentals-fundamentals"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-bioJVM`,
    `fundamentals-bioJS`,
    `fundamentals-json-circeJVM`,
    `fundamentals-json-circeJS`
  )

lazy val `fundamentals-jvm` = (project in file(".agg/fundamentals-fundamentals-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-bioJVM`,
    `fundamentals-json-circeJVM`
  )

lazy val `fundamentals-js` = (project in file(".agg/fundamentals-fundamentals-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-bioJS`,
    `fundamentals-json-circeJS`
  )

lazy val `distage` = (project in file(".agg/distage-distage"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
    `distage-testkit-scalatest`,
    `distage-testkit-legacy`
  )

lazy val `distage-jvm` = (project in file(".agg/distage-distage-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
    `distage-testkit-scalatest`,
    `distage-testkit-legacy`
  )

lazy val `distage-js` = (project in file(".agg/distage-distage-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
    `distage-testkit-scalatest`,
    `distage-testkit-legacy`
  )

lazy val `distage-native` = (project in file(".agg/distage-distage-native"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .aggregate(
    `distage-core-proxy-cglib`,
    `distage-extension-config`,
    `distage-extension-plugins`,
    `distage-framework-api`,
    `distage-framework`,
    `distage-framework-docker`,
    `distage-testkit-core`,
    `distage-testkit-scalatest`,
    `distage-testkit-legacy`
  )

lazy val `logstage` = (project in file(".agg/logstage-logstage"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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

lazy val `logstage-native` = (project in file(".agg/logstage-logstage-native"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .aggregate(
    `logstage-adapter-slf4j`,
    `logstage-sink-slf4j`
  )

lazy val `doc` = (project in file(".agg/doc-doc"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
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
      "2.12.10",
      "2.13.1"
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
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `microsite`
  )

lazy val `doc-native` = (project in file(".agg/doc-doc-native"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .aggregate(
    `microsite`
  )

lazy val `sbt-plugins` = (project in file(".agg/sbt-plugins-sbt-plugins"))
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

lazy val `sbt-plugins-native` = (project in file(".agg/sbt-plugins-sbt-plugins-native"))
  .settings(
    skip in publish := true
  )
  .aggregate(
    `sbt-izumi-deps`
  )

lazy val `izumi-jvm` = (project in file(".agg/.agg-jvm"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-cross-jvm`,
    `fundamentals-jvm`,
    `distage-jvm`,
    `logstage-jvm`,
    `sbt-plugins-jvm`
  )

lazy val `izumi-js` = (project in file(".agg/.agg-js"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-cross-js`,
    `fundamentals-js`,
    `distage-js`,
    `logstage-js`,
    `sbt-plugins-js`
  )

lazy val `izumi-native` = (project in file(".agg/.agg-native"))
  .settings(
    skip in publish := true,
    crossScalaVersions := Seq(
      "2.12.10",
      "2.13.1"
    ),
    scalaVersion := crossScalaVersions.value.head
  )
  .aggregate(
    `fundamentals-cross-native`,
    `distage-native`,
    `logstage-native`,
    `sbt-plugins-native`
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
      "-language:higherKinds",
      "-explaintypes"
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
    scalacOptions in ThisBuild ++= Seq(
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}"
    ),
    crossScalaVersions := Nil,
    scalaVersion := "2.12.10",
    organization in ThisBuild := "io.7mind.izumi",
    sonatypeProfileName := "io.7mind",
    sonatypeSessionName := s"[sbt-sonatype] ${name.value} ${version.value} ${java.util.UUID.randomUUID}",
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
    scalacOptions in ThisBuild += """-Xmacro-settings:scalatest-version=VExpr(V.scalatest)"""
  )
  .disablePlugins(AssemblyPlugin)
  .aggregate(
    `fundamentals-cross`,
    `fundamentals`,
    `distage`,
    `logstage`,
    `sbt-plugins`
  )
