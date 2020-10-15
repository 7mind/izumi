package izumi.distage.docker

import java.util.UUID

import izumi.distage.docker.ContainerNetworkDef.{ContainerNetwork, ContainerNetworkConfig}
import izumi.distage.docker.Docker.DockerReusePolicy
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.{QuasiAsync, QuasiEffect}
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

  final def make[F[_]: TagK](implicit tag: distage.Tag[Network]): Functoid[Lifecycle[F, Network]] = {
    tag.discard()
    ContainerNetworkDef.resource[F](this, this.getClass.getSimpleName)
  }
}

object ContainerNetworkDef {
  def resource[F[_]](conf: ContainerNetworkDef, prefix: String): (DockerClientWrapper[F], IzLogger, QuasiEffect[F], QuasiAsync[F]) => Lifecycle[F, conf.Network] = {
    new NetworkResource(conf.config, _, prefix, _)(_, _)
  }

  final class NetworkResource[F[_], T](
    config: ContainerNetworkConfig[T],
    client: DockerClientWrapper[F],
    prefixName: String,
    logger: IzLogger,
  )(implicit
    F: QuasiEffect[F],
    P: QuasiAsync[F],
  ) extends Lifecycle.Basic[F, ContainerNetwork[T]] {
    import client.rawClient

    private[this] val prefix: String = prefixName.camelToUnderscores.drop(1).replace("$", "")
    private[this] val networkLabels: Map[String, String] = Map(
      DockerConst.Labels.reuseLabel -> Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse).toString,
      s"${DockerConst.Labels.networkDriverPrefix}.${config.driver}" -> true.toString,
      DockerConst.Labels.namePrefixLabel -> prefix,
    )

    override def acquire: F[ContainerNetwork[T]] = {
      integrationCheckHack {
        if (Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse)) {
          val maxAttempts = 50
          logger.info(s"About to start or find ${prefix -> "network"}, ${maxAttempts -> "max lock retries"}...")
          FileLockMutex.withLocalMutex(logger)(s"distage-container-network-def-$prefix", waitFor = 200.millis, maxAttempts = maxAttempts) {
            val labelsSet = networkLabels.toSet
            val existingNetworks = rawClient
              .listNetworksCmd().exec().asScala.toList
              .sortBy(_.getId)
            existingNetworks
              .find(_.labels.asScala.toSet == labelsSet)
              .fold {
                logger.info(s"No existing network found for ${prefix -> "network"}, will create new...")
                createNewRandomizedNetwork()
              } {
                network =>
                  F.maybeSuspend {
                    val id = network.getId
                    val name = network.getName
                    logger.info(s"Matching network found: ${prefix -> "network"}->$name:$id, will try to reuse...")
                    ContainerNetwork(name, id)
                  }
              }
          }
        } else {
          createNewRandomizedNetwork()
        }
      }
    }

    override def release(resource: ContainerNetwork[T]): F[Unit] = {
      if (Docker.shouldKill(config.reuse, client.clientConfig.globalReuse)) {
        F.maybeSuspend {
          logger.info(s"Going to delete ${prefix -> "network"}->${resource.name}:${resource.id}")
          rawClient.removeNetworkCmd(resource.id).exec()
          ()
        }
      } else {
        F.unit
      }
    }

    private[this] def createNewRandomizedNetwork(): F[ContainerNetwork[T]] = {
      F.maybeSuspend {
        val name = config.name.getOrElse(s"$prefix-${UUID.randomUUID().toString.take(8)}")
        logger.info(s"Going to create new ${prefix -> "network"}->$name")
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
