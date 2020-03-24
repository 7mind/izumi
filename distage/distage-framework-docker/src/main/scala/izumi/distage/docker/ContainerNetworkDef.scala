package izumi.distage.docker

import java.util.UUID

import izumi.distage.docker.ContainerNetworkDef.{ContainerNetwork, ContainerNetworkConfig}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.IzLogger
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

trait ContainerNetworkDef {
  self =>
  type Tag
  type Network = ContainerNetwork[Tag]
  type Config = ContainerNetworkConfig[Tag]
  def config: Config
  def make[F[_]: TagK](implicit tag: distage.Tag[Network]): ProviderMagnet[DIResource[F, Network]] = {
    tag.discard()
    ContainerNetworkDef.resource[F](this, self.getClass.getSimpleName)
  }
}

object ContainerNetworkDef {
  def resource[F[_]](conf: ContainerNetworkDef, prefix: String): (DockerClientWrapper[F], IzLogger, DIEffect[F], DIEffectAsync[F]) => DIResource[F, conf.Network] = {
    new NetworkResource(conf.config, _, prefix, _)(_, _)
  }

  final class NetworkResource[F[_]: DIEffect: DIEffectAsync, T](
    config: ContainerNetworkConfig[T],
    clientw: DockerClientWrapper[F],
    prefixName: String,
    logger: IzLogger,
  ) extends DIResource[F, ContainerNetwork[T]] {
    private[this] val client = clientw.client
    private[this] val prefix = prefixName.camelToUnderscores.drop(1).replace("$", "")
    private[this] val stableLabels: Map[String, String] = Map(
      "distage.reuse" -> shouldReuse(config),
      s"distage.driver.${config.driver}" -> "true",
      s"distage.name.prefix" -> prefix,
    ).map { case (k, v) => k -> v.toString }

    private[this] def shouldReuse(config: ContainerNetworkConfig[T]): Boolean = {
      config.reuse && clientw.clientConfig.allowReuse
    }

    private[this] def createNew(): F[ContainerNetwork[T]] = DIEffect[F].maybeSuspend {
      val name = config.name.getOrElse(s"$prefix-${UUID.randomUUID().toString.take(8)}")
      logger.info(s"Going to create ${name -> "network"}")
      val network = client
        .createNetworkCmd()
        .withName(name)
        .withDriver(config.driver)
        .withLabels(stableLabels.asJava)
        .exec()
      ContainerNetwork(name, network.getId)
    }

    override def acquire: F[ContainerNetwork[T]] = {
      if (config.reuse) {
        FileLockMutex.withLocalMutex(logger)(prefix, waitFor = 1.second, maxAttempts = 10) {
          val labelsSet = stableLabels.toSet
          val existedNetworks = client.listNetworksCmd().exec().asScala.toList
          existedNetworks.find(_.labels.asScala.toSet == labelsSet).fold(createNew()) {
            network =>
              DIEffect[F].pure(ContainerNetwork(network.getName, network.getId))
          }
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
          logger.info(s"Going to delete ${resource.name -> "network"}")
          client.removeNetworkCmd(resource.id).exec()
          ()
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
