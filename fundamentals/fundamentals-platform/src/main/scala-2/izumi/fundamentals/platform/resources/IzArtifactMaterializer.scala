package izumi.fundamentals.platform.resources

import scala.reflect.macros.blackbox
import scala.language.experimental.macros

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
