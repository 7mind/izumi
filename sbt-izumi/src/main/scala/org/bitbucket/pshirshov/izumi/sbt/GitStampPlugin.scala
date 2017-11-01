package org.bitbucket.pshirshov.izumi.sbt

// copypasted from due to obsolete jgit dependency
// https://bitbucket.org/atlassianlabs/sbt-git-stamp
// TODO: port original plugin to newer jgit

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import sbt.Keys._
import sbt._

object GitStampPlugin extends AutoPlugin {

  object Keys {
    lazy val gitObject = settingKey[Git]("Git object")
    lazy val gitRepositoryObject = settingKey[Repository]("Git repository object")
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
    , packageOptions += Def.task {
      val repository = gitRepositoryObject.value
      val head = repository.exactRef(Constants.HEAD)
      val branch = repository.getBranch

      val git = gitObject.value
      val status = git.status.call
      val isClean = status.isClean

      Package.ManifestAttributes(
        "X-Git-Branch" -> branch
        , "X-Git-Repo-Is-Clean" -> isClean.toString
        , "X-Git-Head-Rev" -> ObjectId.toString(head.getObjectId)
      )
    }.value
  )

}
