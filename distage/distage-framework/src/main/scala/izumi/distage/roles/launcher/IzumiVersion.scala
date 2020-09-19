package izumi.distage.roles.launcher

import izumi.fundamentals.platform.resources._

object IzumiVersion {
  final def version(): IzArtifact = {
    import izumi.fundamentals.platform.build.BuildAttributes._
    import izumi.fundamentals.platform.build.MacroParameters._

    IzArtifact(
      IzArtifactId(projectGroupId().getOrElse("???"), artifactName().getOrElse("???")),
      ArtifactVersion(artifactVersion().getOrElse("???")),
      BuildStatus(userName().getOrElse("???"), javaVersion().getOrElse("???"), sbtVersion().getOrElse("???"), buildTimestamp()),
      GitStatus(gitBranch().getOrElse("???"), repoClean = gitRepoClean().getOrElse(false), gitHeadCommit().getOrElse("???")),
    )
  }
}
