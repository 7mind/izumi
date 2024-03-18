package izumi.distage.roles.bundled

import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.fundamentals.platform.resources.*

class BundledRolesModule[F[_]: TagK](version: String) extends ModuleDef with RoleModuleDef {
  make[ArtifactVersion].named("launcher-version").fromValue(ArtifactVersion(version))

  addImplicit[TagK[F]]
  makeRole[ConfigWriter[F]]
  makeRole[Help[F]]
  makeRole[RunAllTasks[F]]
  makeRole[RunAllRoles[F]]
}

object BundledRolesModule {
  def apply[F[_]: TagK](implicit izArtifact: IzArtifactMaterializer): BundledRolesModule[F] = new BundledRolesModule(izArtifact.get.version.version)
  def apply[F[_]: TagK](version: String): BundledRolesModule[F] = new BundledRolesModule(version)
}
