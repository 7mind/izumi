package izumi.fundamentals.platform.resources

final case class IzArtifactMaterializer(get: IzArtifact) extends AnyVal

object IzArtifactMaterializer {
  @inline def currentArtifact(implicit ev: IzArtifactMaterializer): IzArtifact = ev.get

  inline implicit def materialize: IzArtifactMaterializer = {
    import izumi.fundamentals.platform.build.BuildAttributes as BA
    import izumi.fundamentals.platform.build.MacroParameters as MP

    new IzArtifactMaterializer(
      new IzArtifact(
        new IzArtifactId(MP.projectGroupId().getOrElse("???"), MP.artifactName().getOrElse("???")),
        new ArtifactVersion(MP.artifactVersion().getOrElse("???")),
        new BuildStatus(BA.userName().getOrElse("???"), BA.javaVersion().getOrElse("???"), MP.sbtVersion().getOrElse("???"), BA.buildTimestamp()),
        new GitStatus(MP.gitBranch().getOrElse("???"), MP.gitRepoClean().getOrElse(false), MP.gitHeadCommit().getOrElse("???")),
      )
    )
  }
}
