package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{ContainerConfig, ContainerNetwork, DockerPort}

object ZookeeperDocker extends ContainerDef {
  val zookeeperNetwork: ContainerNetwork = ContainerNetwork("zookeeper-network")
  override def config: Config = {
    ContainerConfig(
      image = "zookeeper:3.4.13",
      ports = Seq(DockerPort.TCP(2181), DockerPort.TCP(2888), DockerPort.TCP(3888)),
      name = Some("zookeeper-service"),
      networks = Seq(zookeeperNetwork)
    )
  }
}

class ZookeeperDockerModule[F[_]: TagK] extends ModuleDef {
  make[ZookeeperDocker.Container].fromResource {
    ZookeeperDocker.make[F]
  }
}
