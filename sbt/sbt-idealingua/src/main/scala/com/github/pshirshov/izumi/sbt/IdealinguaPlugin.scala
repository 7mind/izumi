package com.github.pshirshov.izumi.sbt

import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.CompilerOptions
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, ScalaTranslator}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage}
import sbt.Keys.{sourceGenerators, watchSources, _}
import sbt._
import sbt.internal.util.ConsoleLogger
import sbt.plugins._

object IdealinguaPlugin extends AutoPlugin {

  final case class Scope(source: Path, target: Path)

  sealed trait Mode

  object Mode {

    case object Sources extends Mode

    case object Artifact extends Mode

  }

  final case class Invokation(options: CompilerOptions, mode: Mode)

  object Keys {
    val compilationTargets = settingKey[Seq[Invokation]]("IDL targets")
    val idlDefaultExtensionsScala = settingKey[Seq[ScalaTranslatorExtension]]("Default list of translator extensions for scala")
    val idlDefaultExtensionsTypescript = settingKey[Seq[TypeScriptTranslatorExtension]]("Default list of translator extensions for typescript")
    val idlDefaultExtensionsGolang = settingKey[Seq[GoLangTranslatorExtension]]("Default list of translator extensions for golang")
    val idlDefaultExtensionsCSharp = settingKey[Seq[CSharpTranslatorExtension]]("Default list of translator extensions for csharp")
  }

  import Keys._

  private val logger: ConsoleLogger = ConsoleLogger()

  override def requires = JvmPlugin


  override lazy val projectSettings = Seq(
    idlDefaultExtensionsScala := ScalaTranslator.defaultExtensions ++ Seq(
      CirceDerivationTranslatorExtension
    )

    , idlDefaultExtensionsTypescript := TypeScriptTranslator.defaultExtensions
    , idlDefaultExtensionsGolang := GoLangTranslator.defaultExtensions
    , idlDefaultExtensionsCSharp := CSharpTranslator.defaultExtensions

    , compilationTargets := Seq(
      Invokation(CompilerOptions(IDLLanguage.Scala, idlDefaultExtensionsScala.value), Mode.Sources)
      , Invokation(CompilerOptions(IDLLanguage.Scala, idlDefaultExtensionsScala.value), Mode.Artifact)
      , Invokation(CompilerOptions(IDLLanguage.Typescript, idlDefaultExtensionsTypescript.value), Mode.Artifact)
      , Invokation(CompilerOptions(IDLLanguage.Go, idlDefaultExtensionsGolang.value), Mode.Artifact)
      , Invokation(CompilerOptions(IDLLanguage.CSharp, idlDefaultExtensionsCSharp.value), Mode.Artifact)
    )

    , watchSources += Watched.WatchSource(baseDirectory.value / "src/main/izumi")

    , sourceGenerators in Compile += Def.task {
      val src = sourceDirectory.value.toPath
      val srcManaged = (sourceManaged in Compile).value.toPath
      val resManaged = (resourceManaged in Compile).value.toPath

      val izumiSrcDir = src.resolve("main/izumi")

      val scope = Scope(izumiSrcDir, srcManaged)

      val (scalaTargets, nonScalaTargets) = compilationTargets.value.partition(i => i.options.language == IDLLanguage.Scala)

      val depClasspath = (dependencyClasspath in Compile).value
      val scala_result = compileSources(scope, scalaTargets, depClasspath)

      nonScalaTargets.foreach {
        t =>
          val nonScalaScope = Scope(izumiSrcDir, (resManaged.toFile / s"${t.options.language}").toPath)
          compileSources(nonScalaScope, Seq(t), depClasspath)
      }

      val files = scala_result.flatMap(_.invokation).flatMap(_._2.paths)
      files.map(_.toFile)
    }.taskValue

    , resourceGenerators in Compile += Def.task {
      val idlbase = sourceDirectory.value / "main" / "izumi"
      logger.debug(s"""Generating resources: $idlbase ...""")
      val allModels = (idlbase ** "*.domain").get ++ (idlbase ** "*.model").get
      val mapped = allModels.map {
        f =>
          val relative = idlbase.toPath.relativize(f.toPath)
          val targetPath = ((resourceManaged in Compile).value / "idealingua").toPath.resolve(relative).toFile

          f -> targetPath
      }
      IO.copy(mapped, CopyOptions().withOverwrite(true))
      mapped.map(_._2)
    }.taskValue

    , artifacts ++= {
      val ctargets = compilationTargets.value
      val pname = name.value
      artifactTargets(ctargets, pname).map(_._1)
    }

    , packagedArtifacts := {
      val ctargets = compilationTargets.value
      val pname = name.value
      val src = sourceDirectory.value.toPath
      val versionValue = version.value
      val scalaVersionValue = scalaVersion.value

      val artifacts = artifactTargets(ctargets, pname)

      val artifactFiles = artifacts.map {
        case (a, t) =>
          val targetDir = target.value / "idealingua" / s"${a.name}-${a.classifier.get}-$versionValue-$scalaVersionValue"

          val scope = Scope(src.resolve("main/izumi"), targetDir.toPath)

          val result = doCompile(scope, t, (dependencyClasspath in Compile).value)
          val zipFile = targetDir / s"${a.name}-${a.classifier.get}-$versionValue.zip"
          IO.move(result.sources.toFile, zipFile)
          //IO.zip(result.map(r => (r, scope.target.relativize(r.toPath).toString)), zipFile)
          a -> zipFile
      }.toMap

      packagedArtifacts.value ++ artifactFiles
    }
  )


  private def artifactTargets(ctargets: Seq[Invokation], pname: String): Seq[(Artifact, Invokation)] = {
    ctargets.filter(i => i.mode == Mode.Artifact).map {
      target =>
        Artifact(pname, "src", "zip", target.options.language.toString) -> target
    }
  }

  private def compileSources(scope: Scope, ctargets: Seq[Invokation], classpath: Classpath): Seq[IDLCompiler.Result] = {
    ctargets.filter(i => i.mode == Mode.Sources).map {
      invokation =>
        doCompile(scope, invokation, classpath)
    }
  }

  private def doCompile(scope: Scope, invokation: Invokation, classpath: Classpath): IDLCompiler.Result = {

    val cp = classpath.map(_.data)
    val target = scope.target
    logger.debug(s"""Loading models from $scope...""")

    val toCompile = new LocalModelLoader(scope.source, cp).load()
    if (toCompile.nonEmpty) {
      logger.info(s"""Going to compile the following models: ${toCompile.map(_.domain.id).mkString(",")}""")
    }

    val result = new IDLCompiler(toCompile)
      .compile(target, invokation.options)

    result.invokation.foreach {
      case (id, s) =>
        logger.debug(s"Model $id produced ${s.paths.size} source files...")
    }

    result
  }

  object autoImport {
    lazy val SbtIdealingua: IdealinguaPlugin.type = IdealinguaPlugin
    lazy val IdealinguaPluginKeys: Keys.type = IdealinguaPlugin.Keys
  }

}

