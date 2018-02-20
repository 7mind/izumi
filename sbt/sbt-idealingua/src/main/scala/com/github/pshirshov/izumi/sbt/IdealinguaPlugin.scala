package com.github.pshirshov.izumi.sbt

import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{CompilerOptions, IDLSuccess}
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage, ModelLoader}
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
  }

  private val logger: ConsoleLogger = ConsoleLogger()

  override def requires = JvmPlugin


  override lazy val projectSettings = Seq(
    Keys.compilationTargets := Seq(Invokation(CompilerOptions(IDLLanguage.Scala), Mode.Sources))

    , sourceGenerators in Compile += Def.task {
      val src = sourceDirectory.value.toPath
      val scopes = Seq(
        Scope(src.resolve("main/izumi"), (sourceManaged in Compile).value.toPath)
      )
      compileSources(scopes, Keys.compilationTargets.value)
    }.taskValue

    , sourceGenerators in Test += Def.task {
      val src = sourceDirectory.value.toPath
      val scopes = Seq(
        Scope(src.resolve("test/izumi"), (sourceManaged in Test).value.toPath)
      )
      compileSources(scopes, Keys.compilationTargets.value)
    }.taskValue
  )

  private def compileSources(scopes: Seq[Scope], ctargets: Seq[Invokation]) = {
    ctargets.filter(i => i.options.language == IDLLanguage.Scala && i.mode == Mode.Sources).flatMap {
      invokation =>
        doCompile(scopes, invokation)
    }
  }

  private def doCompile(scopes: Seq[Scope], invokation: Invokation) = {
    scopes.flatMap {
      scope =>
        val toCompile = new ModelLoader(scope.source).load()

        val target = scope.target
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
