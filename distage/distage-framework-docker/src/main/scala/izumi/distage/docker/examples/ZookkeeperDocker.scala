package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{ContainerConfig, DockerPort}

object ZookeeperDocker extends ContainerDef {
  override def config: Config = {
    ContainerConfig(
      image = "zookeeper:3.4.14",
      ports = Seq(DockerPort.TCP(2181), DockerPort.TCP(2888), DockerPort.TCP(3888))
    )
  }
}

class ZookeeperDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaZookeeperNetwork.Network].fromResource{
    KafkaZookeeperNetwork.make[F]
  }
  make[ZookeeperDocker.Container].fromResource {
    ZookeeperDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
  }
}
