package izumi.distage.testkit.distagesuite.fixtures

import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.integration.Docker.{ContainerConfig, DockerPort}
import izumi.distage.testkit.integration.{ContainerDecl, _}

sealed trait PgContainer
object PgContainer extends ContainerDecl[PgContainer] {
  override def config: ContainerConfig = ContainerConfig(
    "library/postgres",
    Set(DockerPort.TCP(5432)),
  )
}

class PgSvcExample(val pg: PgContainer.Type) {

}

object DockerPlugin extends DockerContainerModule[zio.IO[Throwable, *]] with PluginDef {
  make[PgContainer.Type].fromResource(DockerContainerResource[zio.IO[Throwable, *]].make(PgContainer))

  make[PgSvcExample]
}
