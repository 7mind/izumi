package izumi.fundamentals.platform.resources

import java.io.FileInputStream
import java.util.jar
import java.util.jar.JarFile

import izumi.fundamentals.platform.resources.IzResources.ResourceLocation
import izumi.fundamentals.platform.time.IzTime.{EPOCH, stringToParseableTime}

import scala.reflect.ClassTag
import scala.util.{Success, Try}

object IzManifest extends IzManifest

trait IzManifest {
  val GitBranch: String = "X-Git-Branch"
  val GitRepoIsClean: String = "X-Git-Repo-Is-Clean"
  val GitHeadRev: String = "X-Git-Head-Rev"
  val BuiltBy: String = "X-Built-By"
  val BuildJdk: String = "X-Build-JDK"
  val BuildSbt: String = "X-Build-SBT"

  val Version: String = "X-Version"
  val ImplementationVersion: String = "Implementation-Version"
  val VersionJava: String = "Version"
  val VersionBundle: String = "Bundle-Version"

  val BuildTimestamp: String = "X-Build-Timestamp"
  val ArtifactId: String = "Specification-Title"
  val GroupId: String = "Specification-Vendor"

  val ManifestName: String = JarFile.MANIFEST_NAME

  def manifest[C: ClassTag](filename: String = ManifestName): Option[jar.Manifest] = {
    IzResources.jarResource[C](filename) match {
      case ResourceLocation.Filesystem(file) =>
        val is = new FileInputStream(file)
        try {
          Some(new jar.Manifest(is))
        } finally {
          is.close()
        }

      case ResourceLocation.Jar(_, jar, _) =>
        try {
          Option(jar.getManifest)
        } finally {
          jar.close()
        }

      case ResourceLocation.NotFound =>
        None
    }
  }

  def manifestCl(filename: String = ManifestName): Option[jar.Manifest] = {
    IzResources.read(filename).map(new jar.Manifest(_))
  }

  def read(mf: jar.Manifest): IzArtifact = {
    IzArtifact(
      id = artifactId(mf),
      version = appVersion(mf),
      build = appBuild(mf),
      git = gitStatus(mf),
    )
  }

  protected def gitStatus(mf: jar.Manifest): GitStatus = {
    (Option(mf.getMainAttributes.getValue(GitBranch))
      , Try(mf.getMainAttributes.getValue(GitRepoIsClean).toBoolean).toOption
      , Option(mf.getMainAttributes.getValue(GitHeadRev))
    ) match {
      case (Some(branch), Some(clean), Some(rev)) =>
        GitStatus(branch, clean, rev)
      case _ =>
        GitStatus("?branch", repoClean = false, "?revision")
    }
  }


  protected def artifactId(mf: jar.Manifest): IzArtifactId = {
    (Option(mf.getMainAttributes.getValue(ArtifactId))
      ,Option(mf.getMainAttributes.getValue(GroupId))
    ) match {
      case (Some(art), Some(group)) =>
        IzArtifactId(group, art)
      case _ =>
        IzArtifactId("?group", "?artifact")
    }
  }

  protected def appVersion(mf: jar.Manifest): ArtifactVersion = {
    getFirst(mf)(Seq(Version, ImplementationVersion, VersionJava, VersionBundle)) match {
      case Some(version) =>
        ArtifactVersion(version)
      case _ =>
        ArtifactVersion("0.0.0-?")
    }
  }

  protected def appBuild(mf: jar.Manifest): BuildStatus = {
    (
      Option(mf.getMainAttributes.getValue(BuiltBy))
      , Option(mf.getMainAttributes.getValue(BuildJdk))
      , Option(mf.getMainAttributes.getValue(BuildSbt))
      , Try(Option(mf.getMainAttributes.getValue(BuildTimestamp)).map(_.toTsZ))
    ) match {
      case (Some(user), Some(jdk), Some(sbt), Success(Some(time))) =>
        BuildStatus(user, jdk, sbt, time)

      case _ =>
        BuildStatus("?user", "?jdk", "?sbt", EPOCH)

    }
  }

  private[this] def getFirst(mf: jar.Manifest)(strings: Seq[String]): Option[String] = {
    strings.foldLeft(Option.empty[String]) {
      case (acc, v) => acc.orElse(Option(mf.getMainAttributes.getValue(v)))
    }
  }
}
