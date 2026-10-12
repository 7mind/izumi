package izumi.distage.docker.bundled

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDefTemplate
import izumi.distage.docker.model.Docker.DockerPort

/**
  * Template for creating customized Zookeeper docker containers.
  *
  * {{{
  * object MyZookeeper extends ZookeeperDockerTemplateDef {
  *   override def version: String = "3.9"
  * }
  *
  * // in ModuleDef:
  * make[MyZookeeper.Container].fromResource(MyZookeeper.make[F])
  * }}}
  *
  * @see [[ZookeeperDocker]] for a ready-to-use default instance
  */
trait ZookeeperDockerTemplateDef extends ContainerDefTemplate {
  self: Singleton =>

  override def image: String = "docker/library/zookeeper"
  override def version: String = "3.9"

  val primaryPort: DockerPort = DockerPort.TCP(2181)

  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = s"$image:$version",
      ports = Seq(primaryPort),
    )
  }
}

object ZookeeperDocker extends ZookeeperDockerTemplateDef

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
