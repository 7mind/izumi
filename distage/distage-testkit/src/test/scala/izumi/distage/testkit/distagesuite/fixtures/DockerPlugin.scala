package izumi.distage.testkit.distagesuite.fixtures

import com.github.dockerjava.api.DockerClient
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.testkit.integration.Docker.{ContainerConfig, DockerPort}
import izumi.distage.testkit.integration._


case class PgContainer(id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int]) extends DockerContainer

object PgContainer {
  implicit object Constructor extends DockerContainerF[PgContainer] {

    override def create(id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int]): PgContainer = {
      PgContainer(id, mapping)
    }
  }
}

class PgSvcExample(pg: PgContainer) {

}

object DockerPlugin extends DockerContainerModule[zio.IO[Throwable, *]] with PluginDef {
  make[PgContainer].fromResource {
    client: DockerClient =>
    new DockerContainerResource[zio.IO[Throwable, *], PgContainer](client, ContainerConfig("library/postgres", Set(DockerPort.TCP(5432))))
  }


  make[DockerContainerF[PgContainer]].fromValue(PgContainer.Constructor)
  make[PgSvcExample]
}
