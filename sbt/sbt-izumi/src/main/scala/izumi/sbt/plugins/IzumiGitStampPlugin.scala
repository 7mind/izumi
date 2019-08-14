package izumi.sbt.plugins

// copypasted https://bitbucket.org/atlassianlabs/sbt-git-stamp from due to obsolete jgit dependency

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import sbt.Keys._
import sbt.{Def, _}
import sbt.internal.util.ConsoleLogger

object IzumiGitStampPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  object Keys {
    lazy val izGitObject = settingKey[Git]("Git object")
    lazy val izGitRepositoryObject = settingKey[Repository]("Git repository object")
    lazy val izGitRevision = settingKey[String]("Git revision")
    lazy val izGitBranch = settingKey[String]("Git branch")
    lazy val izGitIsClean = settingKey[Boolean]("Git working dir status")
  }

  import Keys._

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    izGitObject := {
      new Git(izGitRepositoryObject.value)
    }
    , izGitRepositoryObject := {
      val builder = new FileRepositoryBuilder
      builder.readEnvironment.findGitDir.build
    }
    , izGitRevision := {
      val repository = izGitRepositoryObject.value
      val head = repository.exactRef(Constants.HEAD)
      ObjectId.toString(head.getObjectId)
    }
    , izGitBranch := {
      val repository = izGitRepositoryObject.value
      repository.getBranch
    }
    , izGitIsClean := {
      val git = izGitObject.value
      val status = git.status.call
      status.isClean
    }
    , packageOptions += Def.task {
      val gitValues = Map(
        IzumiManifest.GitBranch -> izGitBranch.value
        , IzumiManifest.GitRepoIsClean -> izGitIsClean.value.toString
        , IzumiManifest.GitHeadRev -> izGitRevision.value
      )

      gitValues.foreach {
        case (k, v) =>
          logger.debug(s"Manifest value: $k = $v")
      }

      Package.ManifestAttributes(gitValues.toSeq: _*)
    }.value
  )


}
