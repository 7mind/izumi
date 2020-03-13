package izumi.distage.docker.examples

import java.util.UUID

import distage.{ModuleDef, TagK}
import izumi.distage.docker.Docker.{ContainerConfig, ContainerNetwork, DockerPort}
import izumi.distage.docker.{ContainerDef, DockerClientWrapper, DockerContainer}
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.logstage.api.IzLogger

object ZookeeperDocker extends ContainerDef {
  val zookeeperNetwork: ContainerNetwork = ContainerNetwork("zookeeper-network")
  override def config: Config = {
    ContainerConfig(
      image = "zookeeper:3.4.13",
      ports = Seq(DockerPort.TCP(2181), DockerPort.TCP(2888), DockerPort.TCP(3888)),
      networks = Seq(zookeeperNetwork),
      reuse = false
    )
  }
}

class ZookeeperDockerModule[F[_]: TagK] extends ModuleDef {
  make[ZookeeperDocker.Container].fromResource {
    (wr: DockerClientWrapper[F], log: IzLogger, eff: DIEffect[F], effAsync: DIEffectAsync[F]) =>
      val randomName = s"zookeeper-${UUID.randomUUID().toString.take(8)}"
      new DockerContainer.Resource[F, ZookeeperDocker.Tag](ZookeeperDocker.config.copy(name = Some(randomName)), wr, log)(eff, effAsync)
  }
}
