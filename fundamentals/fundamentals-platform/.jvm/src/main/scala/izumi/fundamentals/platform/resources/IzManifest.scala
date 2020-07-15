package izumi.fundamentals.platform.resources

import java.io.FileInputStream
import java.util.jar
import java.util.jar.JarFile

import izumi.fundamentals.platform.resources.IzResources.ResourceLocation
import izumi.fundamentals.platform.time.IzTime

import scala.reflect.ClassTag
import scala.util.{Success, Try}

trait IzManifest {
  val GitBranch = "X-Git-Branch"
  val GitRepoIsClean = "X-Git-Repo-Is-Clean"
  val GitHeadRev = "X-Git-Head-Rev"
  val BuiltBy = "X-Built-By"
  val BuildJdk = "X-Build-JDK"
  val BuildSbt = "X-Build-SBT"

  val Version = "X-Version"
  val ImplementationVersion = "Implementation-Version"
  val VersionJava = "Version"
  val VersionBundle = "Bundle-Version"

  val BuildTimestamp = "X-Build-Timestamp"
  val ArtifactId = "Specification-Title"
  val GroupId = "Specification-Vendor"

  val ManifestName: String = JarFile.MANIFEST_NAME

  def manifest[C: ClassTag](): Option[java.util.jar.Manifest] = {
    manifest[C](ManifestName)
  }

  def manifest[C: ClassTag](name: String): Option[java.util.jar.Manifest] = {
    IzResources.jarResource[C](name) match {
      case ResourceLocation.Filesystem(file) =>
        val is = new FileInputStream(file)
        try {
          Some(new java.util.jar.Manifest(is))
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

  def manifestCl(): Option[jar.Manifest] = {
    manifest(ManifestName)
  }

  def manifestCl(name: String): Option[jar.Manifest] = {
    IzResources
      .read(name)
      .map {
        is =>
          new java.util.jar.Manifest(is)
      }
  }

  def read(mf: jar.Manifest): IzArtifact = {
    val git = gitStatus(mf)
    val build = appBuild(mf)
    val version = appVersion(mf)
    val artifact = artifactId(mf)
    IzArtifact(artifact, version, build, git)
  }

  protected def gitStatus(mf: jar.Manifest): GitStatus = {
    (
      Option(mf.getMainAttributes.getValue(GitBranch)),
      Try(mf.getMainAttributes.getValue(GitRepoIsClean).toBoolean).toOption,
      Option(mf.getMainAttributes.getValue(GitHeadRev)),
    ) match {
      case (Some(branch), Some(clean), Some(rev)) =>
        GitStatus(branch, clean, rev)
      case _ =>
        GitStatus("?branch", repoClean = false, "?revision")
    }
  }

  protected def artifactId(mf: jar.Manifest): IzArtifactId = {
    (Option(mf.getMainAttributes.getValue(ArtifactId)), Option(mf.getMainAttributes.getValue(GroupId))) match {
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
    import IzTime._
    (
      Option(mf.getMainAttributes.getValue(BuiltBy)),
      Option(mf.getMainAttributes.getValue(BuildJdk)),
      Option(mf.getMainAttributes.getValue(BuildSbt)),
      Try(Option(mf.getMainAttributes.getValue(BuildTimestamp)).map(_.toTsZ)),
    ) match {
      case (Some(user), Some(jdk), Some(sbt), Success(Some(time))) =>
        BuildStatus(user, jdk, sbt, time)

      case _ =>
        BuildStatus("?user", "?jdk", "?sbt", IzTime.EPOCH)

    }
  }

  private def getFirst(mf: jar.Manifest)(strings: Seq[String]) = {
    strings.foldLeft(Option.empty[String]) {
      case (acc, v) => acc.orElse(Option(mf.getMainAttributes.getValue(v)))
    }
  }
}

object IzManifest extends IzManifest
