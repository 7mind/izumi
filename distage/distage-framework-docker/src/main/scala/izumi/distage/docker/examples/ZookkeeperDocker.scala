package izumi.distage.docker.examples

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort

object ZookeeperDocker extends ContainerDef {
  override def config: Config = {
    Config(
      image = "zookeeper:3.4.14",
      ports = Seq(DockerPort.TCP(2181), DockerPort.TCP(2888), DockerPort.TCP(3888)),
    )
  }
}

class ZookeeperDockerModule[F[_]: TagK] extends ModuleDef {
  make[KafkaZookeeperNetwork.Network].fromResource {
    KafkaZookeeperNetwork.make[F]
  }
  make[ZookeeperDocker.Container].fromResource {
    ZookeeperDocker
      .make[F]
      .connectToNetwork(KafkaZookeeperNetwork)
  }
}

object ZookeeperDockerModule {
  def apply[F[_]: TagK]: ZookeeperDockerModule[F] = new ZookeeperDockerModule[F]
}
