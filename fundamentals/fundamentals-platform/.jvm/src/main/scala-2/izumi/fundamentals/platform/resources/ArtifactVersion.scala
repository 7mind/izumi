package izumi.fundamentals.platform.resources

import izumi.fundamentals.platform.time.IzTime
import izumi.fundamentals.platform.time.IzTime.*

import java.time.{Instant, LocalDateTime}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

case class IzArtifactId(groupId: String, artifactId: String) {
  override def toString: String = s"$groupId:$artifactId"
}

// TODO: full-scale version, with proper parsing & comparators
case class ArtifactVersion(version: String) {
  override def toString: String = version
}

case class BuildStatus(user: String, jdk: String, sbt: String, timestamp: LocalDateTime) {
  override def toString: String = s"$user@${timestamp.isoFormat}, JDK $jdk, SBT $sbt"
}

case class GitStatus(branch: String, repoClean: Boolean, revision: String) {
  override def toString: String = {
    val out = s"""$branch#$revision"""
    if (repoClean) {
      out
    } else {
      s"$out*"
    }
  }
}

case class IzArtifact(id: IzArtifactId, version: ArtifactVersion, build: BuildStatus, git: GitStatus) {
  def shortInfo: String = {
    s"$version @ $git, $id, ${build.timestamp.isoFormat}"
  }

  def justVersion: String = {
    s"$version @ $git, ${build.timestamp.isoFormat}"
  }

  override def toString: String = {
    s"$shortInfo (jdk: ${build.jdk}, by: ${build.user})"
  }
}

object IzArtifact {
  val UNDEFINED = "UNDEFINED"

  def undefined: IzArtifact = IzArtifact(
    IzArtifactId(UNDEFINED, UNDEFINED),
    ArtifactVersion(UNDEFINED),
    BuildStatus(UNDEFINED, UNDEFINED, UNDEFINED, LocalDateTime.ofInstant(Instant.EPOCH, IzTime.TZ_UTC)),
    GitStatus(UNDEFINED, repoClean = false, UNDEFINED),
  )
}

final case class IzArtifactMaterializer(get: IzArtifact) extends AnyVal

object IzArtifactMaterializer {
  @inline def currentArtifact(implicit ev: IzArtifactMaterializer): IzArtifact = ev.get

  implicit def materialize: IzArtifactMaterializer = macro IzArtifactMaterializerMacro.make
}

object IzArtifactMaterializerMacro {
  def make(c: blackbox.Context): c.Expr[IzArtifactMaterializer] = {
    import c.universe.*

    c.Expr[IzArtifactMaterializer] {
      q"""{
          import _root_.izumi.fundamentals.platform.build.{BuildAttributes => BA}
          import _root_.izumi.fundamentals.platform.build.{MacroParameters => MP}

          new ${typeOf[IzArtifactMaterializer]}(new ${typeOf[IzArtifact]}(
            new ${typeOf[IzArtifactId]}(MP.projectGroupId().getOrElse("???"), MP.artifactName().getOrElse("???")),
            new ${typeOf[ArtifactVersion]}(MP.artifactVersion().getOrElse("???")),
            new ${typeOf[BuildStatus]}(BA.userName().getOrElse("???"), BA.javaVersion().getOrElse("???"), MP.sbtVersion().getOrElse("???"), BA.buildTimestamp()),
            new ${typeOf[GitStatus]}(MP.gitBranch().getOrElse("???"), MP.gitRepoClean().getOrElse(false), MP.gitHeadCommit().getOrElse("???")),
          ))
          }
       """
    }
  }
}
