package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.DockerPort

/**
  * Example zookeeper docker.
  *  In addition to Zookeeper docker, sets up on [[KafkaZookeeperNetwork.Network]] for [[KafkaDocker]]
  * You're encouraged to use this definition as a template and modify it to your needs.
  */
object ZookeeperDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(2181)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/zookeeper:3.5",
      ports = Seq(primaryPort),
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
