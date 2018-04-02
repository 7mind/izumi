package com.github.pshirshov.izumi.sbt

import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{CompilerOptions, IDLSuccess}
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.{CirceTranslatorExtension, IDLCompiler, IDLLanguage, TranslatorExtension}
import sbt.Keys.{sourceGenerators, _}
import sbt._
import sbt.internal.util.ConsoleLogger
import sbt.plugins._

object IdealinguaPlugin extends AutoPlugin {

  case class Scope(source: Path, target: Path)

  sealed trait Mode

  object Mode {

    case object Sources extends Mode

    case object Artifact extends Mode

  }

  case class Invokation(options: CompilerOptions, mode: Mode)

  object Keys {
    val compilationTargets = settingKey[Seq[Invokation]]("IDL targets")
    val idlDefaultExtensionsScala = settingKey[Seq[TranslatorExtension]]("Default list of translator extensions for scala")
  }

  private val logger: ConsoleLogger = ConsoleLogger()

  override def requires = JvmPlugin


  override lazy val projectSettings = Seq(
    Keys.idlDefaultExtensionsScala := ScalaTranslator.defaultExtensions ++ Seq(
      CirceTranslatorExtension
    )

    , Keys.compilationTargets := Seq(
      Invokation(CompilerOptions(IDLLanguage.Scala, Keys.idlDefaultExtensionsScala.value), Mode.Sources)
      , Invokation(CompilerOptions(IDLLanguage.Scala, Keys.idlDefaultExtensionsScala.value), Mode.Artifact)
    )

    , sourceGenerators in Compile += Def.task {
      val src = sourceDirectory.value.toPath
      val scopes = Seq(
        Scope(src.resolve("main/izumi"), (sourceManaged in Compile).value.toPath)
      )
      compileSources(scopes, Keys.compilationTargets.value, (dependencyClasspath in Compile).value)
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
      val ctargets = Keys.compilationTargets.value
      val pname = name.value
      artifactTargets(ctargets, pname).map(_._1)
    }

    , packagedArtifacts := {
      val ctargets = Keys.compilationTargets.value
      val pname = name.value
      val src = sourceDirectory.value.toPath
      val versionValue = version.value
      val scalaVersionValue = scalaVersion.value

      val artifacts = artifactTargets(ctargets, pname)

      val artifactFiles = artifacts.map {
        case (a, t) =>
          val targetDir = target.value / "idealingua" / s"${a.name}-${a.classifier.get}-$versionValue-$scalaVersionValue"

          val scope = Scope(src.resolve("main/izumi"), targetDir.toPath)

          val result = doCompile(Seq(scope), t, (dependencyClasspath in Compile).value)
          val zipFile = targetDir / s"${a.name}-${a.classifier.get}-$versionValue.zip"
          IO.zip(result.map(r => (r, scope.target.relativize(r.toPath).toString )), zipFile)
          a -> zipFile
      }.toMap

      packagedArtifacts.value ++ artifactFiles
    }
  )


  private def artifactTargets(ctargets: Seq[Invokation], pname: String) = {
    ctargets.filter(i => i.mode == Mode.Artifact).map {
      target =>
        Artifact(pname, "src", "zip", target.options.language.toString) -> target
    }
  }

  private def compileSources(scopes: Seq[Scope], ctargets: Seq[Invokation], classpath: Classpath) = {
    ctargets.filter(i => i.options.language == IDLLanguage.Scala && i.mode == Mode.Sources).flatMap {
      invokation =>
        doCompile(scopes, invokation, classpath)
    }
  }

  private def doCompile(scopes: Seq[Scope], invokation: Invokation, classpath: Classpath): Seq[File] = {
    scopes.flatMap {
      scope =>
        val cp = classpath.map(_.data)
        val target = scope.target
        logger.debug(s"""Loading models from $scope...""")

        val toCompile = new LocalModelLoader(scope.source, cp).load()
        if (toCompile.nonEmpty) {
          logger.info(s"""Going to compile the following models: ${toCompile.map(_.id).mkString(",")}""")
        }

        toCompile.flatMap {
          domain =>
            logger.info(s"Compiling model ${domain.id} into $target...")
            val compiler = new IDLCompiler(domain)
            compiler.compile(target, invokation.options) match {
              case s: IDLSuccess =>
                logger.debug(s"Model ${domain.id} produces ${s.paths.size} source files...")
                s.paths.map(_.toFile)
              case _ =>
                throw new IllegalStateException(s"Cannot compile model ${domain.id}")
            }
        }

    }
  }

  object autoImport {
    val IdealinguaPlugin = com.github.pshirshov.izumi.sbt.IdealinguaPlugin
  }

}
