package izumi.distage.roles.bundled

import java.time.{Instant, ZonedDateTime}

import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.resources._
import izumi.fundamentals.platform.time.IzTime

import scala.reflect.ClassTag

abstract class BundledRolesModule[F[_]: TagK] extends ModuleDef {
  make[ConfigWriter[F]]
  make[Help[F]]
  make[ArtifactVersion].named("launcher-version").fromValue(ArtifactVersion(versionString))

  protected def versionString: String

  protected final def maybeVersion[T: ClassTag](): Option[IzArtifact] = {
    IzManifest.manifest[T]().map(IzManifest.read)
  }
  protected final def version[T: ClassTag](): IzArtifact = maybeVersion().getOrElse {
    val UNDEFINED = "UNDEFINED"
    IzArtifact(
      IzArtifactId(UNDEFINED, UNDEFINED),
      ArtifactVersion(UNDEFINED),
      BuildStatus(UNDEFINED, UNDEFINED, UNDEFINED, ZonedDateTime.ofInstant(Instant.EPOCH, IzTime.TZ_UTC)),
      GitStatus(UNDEFINED, repoClean = false, UNDEFINED),
    )
  }
}

object BundledRolesModule {
  def apply[F[_]: TagK](version: String): BundledRolesModule[F] = {
    val v = version
    new BundledRolesModule { override def versionString: String = v }
  }
}
