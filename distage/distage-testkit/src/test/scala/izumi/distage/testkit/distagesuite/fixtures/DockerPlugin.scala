package izumi.distage.testkit.distagesuite.fixtures

import com.github.dockerjava.api.DockerClient
import izumi.distage.plugins.PluginDef
import izumi.distage.testkit.integration.Docker.{ContainerConfig, DockerPort}
import izumi.distage.testkit.integration._


//case class PgContainer(id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int]) extends DockerContainer

//object PgContainer {
//  implicit object Constructor extends DockerContainerConstructor[PgContainer] {
//
//    override def create(id: Docker.ContainerId, mapping: Map[Docker.DockerPort, Int]): PgContainer = {
//      PgContainer(id, mapping)
//    }
//  }
//}

sealed trait PgContainer extends ContainerTag
object PgContainer {
  type Type = DockerContainer[PgContainer]
}

class PgSvcExample(val pg: PgContainer.Type) {

}

object DockerPlugin extends DockerContainerModule[zio.IO[Throwable, *]] with PluginDef {
  make[PgContainer.Type].fromResource {
    client: DockerClient =>
    new DockerContainerResource[zio.IO[Throwable, *], PgContainer](client, ContainerConfig("library/postgres", Set(DockerPort.TCP(5432))))
  }

  //make[DockerContainerConstructor[PgContainer]].fromValue(PgContainer.Constructor)
  make[PgSvcExample]
}
