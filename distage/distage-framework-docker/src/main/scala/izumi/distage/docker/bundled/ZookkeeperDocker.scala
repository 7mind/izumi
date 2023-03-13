package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort

object ZookeeperDocker extends ContainerDef {
  override def config: Config = {
    Config(
      image = "zookeeper:3.5",
      ports = Seq(DockerPort.TCP(2181)),
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
