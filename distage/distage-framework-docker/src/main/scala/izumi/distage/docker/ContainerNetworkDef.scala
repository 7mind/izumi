package izumi.distage.docker

import java.util.UUID

import com.github.dockerjava.api.model.Network
import izumi.distage.docker
import izumi.distage.docker.ContainerNetworkDef.{ContainerNetwork, ContainerNetworkConfig}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.IzLogger

import scala.collection.JavaConverters._

trait ContainerNetworkDef {
  self =>
  type Tag
  type Network = ContainerNetwork[Tag]
  type Config = ContainerNetworkConfig[Tag]
  def config: Config
  def make[F[_]: TagK](implicit tag: distage.Tag[Network]): ProviderMagnet[DIResource[F, Network]] = {
    ContainerNetworkDef.resource[F](this, self.getClass.getSimpleName)
  }
}

object ContainerNetworkDef {
  def resource[F[_]](conf: ContainerNetworkDef, prefix: String): (DockerClientWrapper[F], IzLogger, DIEffect[F]) => DIResource[F, conf.Network] = {
    new NetworkResource(conf.config, _, prefix, _)(_)
  }

  final class NetworkResource[F[_]: DIEffect, T](
    config: ContainerNetworkConfig[T],
    clientw: DockerClientWrapper[F],
    prefixName: String,
    logger: IzLogger,
  ) extends DIResource[F, ContainerNetwork[T]] {
    private[this] val client = clientw.client
    private[this] val prefix = prefixName.camelToUnderscores
    val stableLabels: Map[String, String] = Map(
      "distage.reuse" -> shouldReuse(config),
      s"distage.driver.${config.driver}" -> "true",
      s"distage.name.prefix" -> prefix,
    ).mapValues(_.toString)

    private[this] def shouldReuse(config: ContainerNetworkConfig[T]): Boolean = {
      config.reuse && clientw.clientConfig.allowReuse
    }

    private[this] def createNew(): ContainerNetwork[T] = {
      val name = config.name.getOrElse(s"$prefix-${UUID.randomUUID().toString.take(8)}")
      val network = client
        .createNetworkCmd()
        .withName(name)
        .withDriver(config.driver)
        .withLabels(stableLabels.asJava)
        .exec()
      ContainerNetwork(name, network.getId)
    }

    override def acquire: F[ContainerNetwork[T]] = DIEffect[F].maybeSuspend {
      if (config.reuse) {
        val existedNetworks: List[Network] = client.listNetworksCmd().exec().asScala.toList
        existedNetworks.find(_.labels == stableLabels).fold(createNew()) {
          network =>
            ContainerNetwork(network.getName, network.getId)
        }
      } else {
        createNew()
      }
    }

    override def release(resource: ContainerNetwork[T]): F[Unit] = {
      if (shouldReuse(config)) {
        DIEffect[F].unit
      } else {
        DIEffect[F].maybeSuspend {
          client.removeNetworkCmd(resource.id).exec()
        }
      }
    }
  }

  final case class ContainerNetwork[Tag](
    name: String,
    id: String
  )

  final case class ContainerNetworkConfig[Tag](
    name: Option[String] = None,
    driver: String = "bridge",
    reuse: Boolean = true
  )
}
