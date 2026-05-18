package izumi.distage.docker

import izumi.distage.docker.ContainerNetworkDef.{ContainerNetwork, ContainerNetworkConfig}
import izumi.distage.docker.impl.DockerClientWrapper
import izumi.distage.docker.model.Docker.DockerReusePolicy
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.{Async2, IO2, Primitives2, Temporal2}
import izumi.fundamentals.platform.files.FileLockMutex
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks.*
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger
import izumi.reflect.TagKK

import java.util.UUID
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

trait ContainerNetworkDef {
  // `ContainerNetworkDef`s must be top-level objects, otherwise `.Network` and `.Config` won't be referencable in ModuleDef
  self: Singleton =>

  type Tag

  final type Network = ContainerNetwork[Tag]

  final type Config = ContainerNetworkConfig[Tag]
  final lazy val Config = ContainerNetworkConfig

  def config: Config

  final def make[F[+_, +_]: TagKK](implicit tag: distage.Tag[self.Tag]): Functoid[Lifecycle[F, Throwable, Network]] = {
    tag.discard()
    ContainerNetworkDef.resource[F](this, this.getClass.getSimpleName)
  }
}

object ContainerNetworkDef {
  type Aux[T] = ContainerNetworkDef { type Tag = T }

  def resource[F[+_, +_]](
    conf: ContainerNetworkDef,
    prefix: String,
  ): (DockerClientWrapper[F], IzLogger, IO2[F], Async2[F], Temporal2[F], Primitives2[F]) => Lifecycle[F, Throwable, conf.Network] = {
    new NetworkResource(conf.config, _, prefix, _)(_, _, _, _)
  }

  final class NetworkResource[F[+_, +_], T](
    config: ContainerNetworkConfig[T],
    client: DockerClientWrapper[F],
    prefixName: String,
    logger: IzLogger,
  )(implicit
    F: IO2[F],
    P: Async2[F],
    T: Temporal2[F],
    Prim: Primitives2[F],
  ) extends Lifecycle.Basic[F, Throwable, ContainerNetwork[T]] {
    import client.rawClient

    private val prefix: String = prefixName.camelToUnderscores.replace("$", "")
    private val networkLabels: Map[String, String] = Map(
      DockerConst.Labels.reuseLabel -> Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse).toString,
      s"${DockerConst.Labels.networkDriverPrefix}.${config.driver}" -> true.toString,
      DockerConst.Labels.namePrefixLabel -> prefix,
    )

    override def acquire: F[Throwable, ContainerNetwork[T]] = {
      integrationCheckHack {
        if (Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse)) {
          val retryWait = 200.millis
          val maxWait = 10.seconds
          val maxAttempts = (maxWait / retryWait).toInt

          logger.info(s"About to start or find ${prefix -> "network"}, ${maxAttempts -> "max lock retries"}...")

          val filename = s"distage-container-network-def-$prefix"

          def acquireContainerNetwork: F[Throwable, ContainerNetwork[T]] = {
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
                  F.syncThrowable {
                    val id = network.getId
                    val name = network.getName
                    logger.info(s"Matching network found: ${prefix -> "network"}->$name:$id, will try to reuse...")
                    ContainerNetwork(name, id)
                  }
              }
          }
          FileLockMutex.withLocalMutex[F, ContainerNetwork[T]](
            filename = filename,
            retryWait = retryWait,
            maxAttempts = maxAttempts,
            attemptLog = (num, maxAttempts) => F.syncThrowable(logger.debug(s"Attempt $num out of $maxAttempts to acquire file lock for image $filename.")),
            lockAlreadyExistedLog = F.syncThrowable(logger.debug(s"File lock already existed for image $filename")),
          )(
            fail = attempts =>
              F.syncThrowable(logger.warn(s"Cannot acquire file lock for image $filename after $attempts. This may lead to creation of a new duplicate container"))
                .flatMap(_ => acquireContainerNetwork),
            succ = _ => acquireContainerNetwork,
          )
        } else {
          createNewRandomizedNetwork()
        }
      }
    }

    override def release(resource: ContainerNetwork[T]): F[Nothing, Unit] = {
      if (Docker.shouldKillPromptly(config.reuse, client.clientConfig.globalReuse)) {
        F.sync {
          logger.info(s"Going to delete ${prefix -> "network"}->${resource.name}:${resource.id}")
          try {
            rawClient.removeNetworkCmd(resource.id).exec()
          } catch {
            case t: Throwable =>
              logger.warn(s"Failed to delete network ${resource.name}:${resource.id}, $t")
          }
          ()
        }
      } else {
        F.unit
      }
    }

    private def createNewRandomizedNetwork(): F[Throwable, ContainerNetwork[T]] = {
      F.syncThrowable {
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

    private def integrationCheckHack[A](f: => F[Throwable, A]): F[Throwable, A] = {
      // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
      F.sandboxCatchAll[Throwable, A, Throwable](f) {
        exit =>
          F.fail(new IntegrationCheckException(ResourceCheck.ResourceUnavailable(exit.toThrowable.getMessage, Some(exit.toThrowable))))
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
