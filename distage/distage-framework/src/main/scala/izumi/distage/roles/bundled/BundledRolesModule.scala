package izumi.distage.roles.bundled

import distage.TagKK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.fundamentals.platform.resources.*
import izumi.fundamentals.platform.versions.Version

class BundledRolesModule[F[+_, +_]: TagKK](version: Version) extends ModuleDef with RoleModuleDef {
  make[ArtifactVersion].named("launcher-version").fromValue(ArtifactVersion(version))

  makeRole[ConfigWriter[F]]
  makeRole[Help[F]]
  makeRole[RunAllTasks[F]]
  makeRole[RunAllRoles[F]]
}

object BundledRolesModule {
  def apply[F[+_, +_]: TagKK](implicit izArtifact: IzArtifactMaterializer): BundledRolesModule[F] = new BundledRolesModule(izArtifact.get.version.version)
  def apply[F[+_, +_]: TagKK](version: Version): BundledRolesModule[F] = new BundledRolesModule(version)
}
