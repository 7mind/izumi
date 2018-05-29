package com.github.pshirshov.izumi.sbt.plugins.optional

import java.io.File

import IzumiPublishingPlugin.Keys.publishTargets
import IzumiPublishingPlugin.MavenTarget
import coursier.Fetch.Metadata
import coursier._
import coursier.core.Authentication
import coursier.maven.MavenRepository
import coursier.util._
import sbt.Keys._
import sbt.internal.util.ConsoleLogger
import sbt.io.{CopyOptions, IO}
import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.ivy.{DirectCredentials, FileCredentials}
import sbt.{AutoPlugin, Def, settingKey, taskKey, librarymanagement => lm}

object IzumiFetchPlugin extends AutoPlugin {

  object Keys {
    lazy val fetchArtifacts = settingKey[Seq[ModuleID]]("Jars to fetch from outside")
    lazy val artifactsTargetDir = settingKey[File]("Jars to fetch from outside")
    lazy val resolveArtifacts = taskKey[Seq[File]]("Performs transitive resolution with Coursier")
    lazy val copyArtifacts = taskKey[Unit]("Copy artifacts into target directory")

  }

  import Keys._
  protected val logger: ConsoleLogger = ConsoleLogger()


  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    fetchArtifacts := Seq.empty
    , artifactsTargetDir := {
      import sbt.io.syntax._
      target.value / "artifacts"
    }
    , resolveArtifacts := Def.task {
      import CoursierCompat._

      val sbtRepos = resolvers.value.collect {
        case m: lm.MavenRepository =>
          m.root -> m.toCoursier
      }.toMap
      val ownRepos = publishTargets.value.map(t =>t.repo.root -> t.toCoursier)
      val repos = (sbtRepos ++ ownRepos).values.toSeq

      val scala = scalaBinaryVersion.value
      val deps = Keys.fetchArtifacts.value.map(_.toCoursier(scala))

      logger.info(s"Fetching external artifacts: ${deps.mkString("- ", "\n- ", "")}")
      val resolved = CoursierFetch.resolve(repos, deps)
      resolved
    }.value
    , copyArtifacts := Def.task {
      val targetDir = artifactsTargetDir.value
      IO.createDirectory(targetDir)
      val resolved = resolveArtifacts.value
      IO.copy(resolved.map(r => (r, targetDir.toPath.resolve(r.getName).toFile)), CopyOptions(overwrite = true, preserveLastModified = true, preserveExecutable = true))
    }
    , packageBin in lm.syntax.Compile := Def.taskDyn {
      copyArtifacts.value

      val ctask = (packageBin in lm.syntax.Compile).value
      Def.task {
        ctask
      }
    }.value
  )
}

object CoursierCompat {
  val repositories = Seq(
    MavenRepository("https://repo1.maven.org/maven2")
  )

  implicit class SbtRepoExt(repository: lm.MavenRepository) {
    def toCoursier: MavenRepository = {
      MavenRepository(repository.root)
    }
  }

  implicit class IzumiExt(target: MavenTarget) {
    def toCoursier: MavenRepository = {
      val creds = target.credentials match {
        case f: FileCredentials =>
          lm.ivy.Credentials.loadCredentials(f.path).right.get
        case d: DirectCredentials =>
          d
      }

      val auth = Authentication(creds.userName, creds.passwd)
      target.repo.toCoursier.copy(authentication = Some(auth))
    }
  }

  implicit class ModuleIdExt(module: lm.ModuleID) {
    def toCoursier(scalaVersion: String): Dependency = {
      val name = module.crossVersion match {
        case b: lm.Binary =>
          val suffix = Option(b.suffix).filter(_.nonEmpty).getOrElse(scalaVersion)
          Seq(b.prefix, module.name, suffix).filter(_.nonEmpty).mkString("_")

        case _: lm.Disabled =>
          module.name

        case _ =>
          throw new IllegalArgumentException(s"Unexpected crossversion in $module")
      }

      Dependency(Module(module.organization,name, module.extraAttributes),
        module.revision,
        attributes = Attributes(`type` = "jar")
      )
    }
  }

}

object CoursierFetch {
  def resolve(repositories: Seq[MavenRepository], modules: Seq[Dependency]): Seq[File] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val withCache = Seq(Cache.ivy2Local) ++ repositories
    val start: Resolution = Resolution(modules.toSet)
    val fetch: Metadata[Task] = Fetch.from(withCache, Cache.fetch[Task]())
    val resolution = start.process.run(fetch).unsafeRun()
    val localArtifacts: Seq[Either[FileError, File]] = Gather[Task].gather(
      resolution.artifacts.map(Cache.file[Task](_).run)
    ).unsafeRun()

    localArtifacts.map(_.right.get)
  }
}
