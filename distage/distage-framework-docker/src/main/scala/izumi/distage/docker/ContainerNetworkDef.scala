package izumi.distage.docker

import java.util.UUID

import izumi.distage.docker.ContainerNetworkDef.{ContainerNetwork, ContainerNetworkConfig}
import izumi.distage.docker.Docker.DockerReusePolicy
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait ContainerNetworkDef {
  type Tag
  final type Network = ContainerNetwork[Tag]
  final type Config = ContainerNetworkConfig[Tag]
  final val Config = ContainerNetworkConfig

  def config: Config

  final def make[F[_]: TagK](implicit tag: distage.Tag[Network]): Functoid[DIResource[F, Network]] = {
    tag.discard()
    ContainerNetworkDef.resource[F](this, this.getClass.getSimpleName)
  }
}

object ContainerNetworkDef {
  def resource[F[_]](conf: ContainerNetworkDef, prefix: String): (DockerClientWrapper[F], IzLogger, DIEffect[F], DIEffectAsync[F]) => DIResource[F, conf.Network] = {
    new NetworkResource(conf.config, _, prefix, _)(_, _)
  }

  final class NetworkResource[F[_], T](
    config: ContainerNetworkConfig[T],
    client: DockerClientWrapper[F],
    prefixName: String,
    logger: IzLogger,
  )(implicit
    F: DIEffect[F],
    P: DIEffectAsync[F],
  ) extends DIResource[F, ContainerNetwork[T]] {
    private[this] val rawClient = client.rawClient
    private[this] val prefix: String = prefixName.camelToUnderscores.drop(1).replace("$", "")
    private[this] val networkLabels: Map[String, String] = Map(
      DockerConst.Labels.reuseLabel -> Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse).toString,
      s"${DockerConst.Labels.networkDriverPrefix}.${config.driver}" -> true.toString,
      DockerConst.Labels.namePrefixLabel -> prefix,
    )

    override def acquire: F[ContainerNetwork[T]] = {
      integrationCheckHack {
        if (Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse)) {
          FileLockMutex.withLocalMutex(logger)(prefix, waitFor = 1.second, maxAttempts = 10) {
            val labelsSet = networkLabels.toSet
            val existedNetworks = rawClient.listNetworksCmd().exec().asScala.toList
            existedNetworks.find(_.labels.asScala.toSet == labelsSet).fold(createNew()) {
              network =>
                F.pure(ContainerNetwork(network.getName, network.getId))
            }
          }
        } else {
          createNew()
        }
      }
    }

    override def release(resource: ContainerNetwork[T]): F[Unit] = {
      if (Docker.shouldKill(config.reuse, client.clientConfig.globalReuse)) {
        F.maybeSuspend {
          logger.info(s"Going to delete ${resource.name -> "network"}")
          rawClient.removeNetworkCmd(resource.id).exec()
          ()
        }
      } else {
        F.unit
      }
    }

    private[this] def createNew(): F[ContainerNetwork[T]] = {
      F.maybeSuspend {
        val name = config.name.getOrElse(s"$prefix-${UUID.randomUUID().toString.take(8)}")
        logger.info(s"Going to create ${name -> "network"}")
        val network = rawClient
          .createNetworkCmd()
          .withName(name)
          .withDriver(config.driver)
          .withLabels(networkLabels.asJava)
          .exec()
        ContainerNetwork(name, network.getId)
      }
    }

    private[this] def integrationCheckHack[A](f: => F[A]): F[A] = {
      // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
      F.definitelyRecover(f) {
        c: Throwable =>
          F.fail(new IntegrationCheckException(NonEmptyList(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c)))))
      }
    }

  }

  final case class ContainerNetwork[Tag](
    name: String,
    id: String,
  )

  final case class ContainerNetworkConfig[Tag](
    name: Option[String] = None,
    driver: String = "bridge",
    reuse: DockerReusePolicy = DockerReusePolicy.ReuseEnabled,
  )
}
