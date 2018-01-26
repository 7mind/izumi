package org.bitbucket.pshirshov.izumi.sbt

// copypasted https://bitbucket.org/atlassianlabs/sbt-git-stamp from due to obsolete jgit dependency

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import sbt.Keys._
import sbt._
import sbt.internal.util.ConsoleLogger

object GitStampPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  object Keys {
    lazy val gitObject = settingKey[Git]("Git object")
    lazy val gitRepositoryObject = settingKey[Repository]("Git repository object")
    lazy val gitRevision = settingKey[String]("Git revision")
    lazy val gitBranch = settingKey[String]("Git branch")
    lazy val gitIsClean = settingKey[Boolean]("Git working dir status")
  }

  import Keys._

  override def globalSettings = Seq(
    gitObject := {
      new Git(gitRepositoryObject.value)
    }
    , gitRepositoryObject := {
      val builder = new FileRepositoryBuilder
      builder.readEnvironment.findGitDir.build
    }
    , gitRevision := {
      val repository = gitRepositoryObject.value
      val head = repository.exactRef(Constants.HEAD)
      ObjectId.toString(head.getObjectId)
    }
    , gitBranch := {
      val repository = gitRepositoryObject.value
      repository.getBranch
    }
    , gitIsClean := {
      val git = gitObject.value
      val status = git.status.call
      status.isClean
    }
    , packageOptions += Def.task {
      val gitValues = Map(
        "X-Git-Branch" -> gitBranch.value
        , "X-Git-Repo-Is-Clean" -> gitIsClean.value.toString
        , "X-Git-Head-Rev" -> gitRevision.value
      )

      gitValues.foreach {
        case (k, v) =>
          logger.info(s"Manifest value: $k = $v")
      }

      Package.ManifestAttributes(gitValues.toSeq :_*)
    }.value
  )

}
