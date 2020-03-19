package izumi.distage.docker

import java.util.concurrent.TimeUnit

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.ghik.silencer.silent
import distage.TagK
import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer.ContainerResource
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.DIResource
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.functional.Value
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.network.IzSockets
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait ContainerDef {
  self =>
  type Tag
  type Container = DockerContainer[Tag]
  type Config = Docker.ContainerConfig[Tag]

  def config: Config

  /**
    * For binding in `ModuleDef`:
    *
    * {{{
    * object KafkaDocker extends ContainerDef
    * object ZookeeperDocker extends ContainerDef
    *
    * make[KafkaDocker.Container].fromResource {
    *   KafkaDocker
    *     .make[F]
    *     .dependOnDocker(ZookeeperDocker)
    * }
    * }}}
    *
    * To kill all containers spawned by distage, use the following command:
    *
    * {{{
    *   docker rm -f $(docker ps -q -a -f 'label=distage.type')
    * }}}
    *
    */
  final def make[F[_]: TagK](implicit tag: distage.Tag[Tag]): ProviderMagnet[ContainerResource[F, Tag] with DIResourceBase[F, Container]] = {
    tag.discard()
    DockerContainer.resource[F](this)
  }

  final def copy(config: Config): ContainerDef.Aux[self.Tag] = {
    @inline def c = config
    new ContainerDef {
      override type Tag = self.Tag
      override def config: Config = c
    }
  }
}

object ContainerDef {
  type Aux[T] = ContainerDef { type Tag = T }
}

final case class DockerContainer[Tag](
  id: Docker.ContainerId,
  name: String,
  hostName: String,
  ports: Map[Docker.DockerPort, Seq[ServicePort]],
  containerConfig: Docker.ContainerConfig[Tag],
  clientConfig: ClientConfig,
  availablePorts: Map[Docker.DockerPort, Seq[AvailablePort]],
) {
  override def toString: String = s"$name:${id.name} ports=${ports.mkString("{", ", ", "}")} available=${availablePorts.mkString("{", ", ", "}")}"
}

object DockerContainer {
  def resource[F[_]](conf: ContainerDef): (DockerClientWrapper[F], IzLogger, DIEffect[F], DIEffectAsync[F]) => ContainerResource[F, conf.Tag] = {
    new ContainerResource[F, conf.Tag](conf.config, _, _)(_, _)
  }

  implicit final class DockerProviderExtensions[F[_], T](private val self: ProviderMagnet[ContainerResource[F, T]]) extends AnyVal {
    /**
      * Allows you to modify [[ContainerConfig]] while summoning additional dependencies from the object graph using [[ProviderMagnet]].
      *
      * Example:
      *
      * {{{
      *   KafkaDocker
      *     .make[F]
      *     .modifyConfig {
      *       (zookeeperDocker: ZookeeperDocker.Container, net: KafkaZookeeperNetwork.Network) =>
      *         (old: KafkaDocker.Config) =>
      *           val zkEnv = KafkaDocker.config.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
      *           val zkNet = KafkaDocker.config.networks + net
      *           old.copy(env = zkEnv, networks = zkNet)
      *     }
      * }}}
      *
      */
    def modifyConfig(
      modify: ProviderMagnet[Docker.ContainerConfig[T] => Docker.ContainerConfig[T]]
    )(implicit tag1: distage.Tag[ContainerResource[F, T]], tag2: distage.Tag[Docker.ContainerConfig[T]]): ProviderMagnet[ContainerResource[F, T]] = {
      tag2.discard()
      self.zip(modify).map {
        case (that, f) =>
          import that._
          that.copy(config = f(that.config))
      }
    }

    def dependOnDocker(containerDecl: ContainerDef)(implicit tag: distage.Tag[DockerContainer[containerDecl.Tag]]): ProviderMagnet[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[containerDecl.Tag]]
    }

    def dependOnDocker[T2](implicit tag: distage.Tag[DockerContainer[T2]]): ProviderMagnet[ContainerResource[F, T]] = {
      self.addDependency[DockerContainer[T2]]
    }

    def connectToNetwork[T2](
      implicit tag1: distage.Tag[ContainerNetworkDef.ContainerNetwork[T2]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]]
    ): ProviderMagnet[ContainerResource[F, T]] = {
      tag1.discard()
      modifyConfig {
        net: ContainerNetworkDef.ContainerNetwork[T2] => old: Docker.ContainerConfig[T] =>
          old.copy(networks = old.networks + net)
      }
    }

    def connectToNetwork(
      networkDecl: ContainerNetworkDef
    )(
      implicit tag1: distage.Tag[ContainerNetworkDef.ContainerNetwork[networkDecl.Tag]],
      tag2: distage.Tag[ContainerResource[F, T]],
      tag3: distage.Tag[Docker.ContainerConfig[T]]
    ): ProviderMagnet[ContainerResource[F, T]] = {
      tag1.discard()
      modifyConfig {
        net: ContainerNetworkDef.ContainerNetwork[networkDecl.Tag] => old: Docker.ContainerConfig[T] =>
          old.copy(networks = old.networks + net)
      }
    }
  }

  private[this] final case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])

  case class ContainerResource[F[_], T](
    config: Docker.ContainerConfig[T],
    clientw: DockerClientWrapper[F],
    logger: IzLogger,
  )(
    implicit
    val F: DIEffect[F],
    val P: DIEffectAsync[F]
  ) extends DIResource[F, DockerContainer[T]] {

    private[this] val client = clientw.client

    private[this] def toExposedPort(port: DockerPort): ExposedPort = {
      port match {
        case DockerPort.TCP(number) =>
          ExposedPort.tcp(number)
        case DockerPort.UDP(number) =>
          ExposedPort.udp(number)
      }
    }

    private[this] def shouldReuse(config: Docker.ContainerConfig[T]): Boolean = {
      config.reuse && clientw.clientConfig.allowReuse
    }

    override def acquire: F[DockerContainer[T]] = F.suspendF {
      val ports = config.ports.map {
        containerPort =>
          val local = IzSockets.temporaryLocalPort()
          val bp = toExposedPort(containerPort)
          val binding = new PortBinding(Ports.Binding.bindPort(local), bp)
          val stableLabels = Map(
            "distage.reuse" -> shouldReuse(config).toString,
            s"distage.port.${containerPort.protocol}.${containerPort.number}.defined" -> "true",
          )
          val labels = Map(
            s"distage.port.${containerPort.protocol}.${containerPort.number}" -> local.toString,
          )
          PortDecl(containerPort, local, binding, stableLabels ++ labels)
      }

      if (shouldReuse(config)) {
        runReused(ports)
      } else {
        doRun(ports)
      }
    }
    override def release(resource: DockerContainer[T]): F[Unit] = {
      if (!shouldReuse(resource.containerConfig)) {
        clientw.destroyContainer(resource.id)
      } else {
        F.unit
      }
    }

    def await(container: DockerContainer[T]): F[DockerContainer[T]] = {
      F.maybeSuspend {
        logger.debug(s"Awaiting until $container gets alive")
        try {
          val status = client.inspectContainerCmd(container.id.name).exec()
          if (status.getState.getRunning) {
            config.healthCheck.check(logger, container)
          } else {
            HealthCheckResult.Failed(new RuntimeException(s"Container exited: ${container.id}, full status: $status"))
          }
        } catch {
          case t: Throwable =>
            HealthCheckResult.Failed(t)
        }
      }.flatMap {
        case HealthCheckResult.JustRunning =>
          F.maybeSuspend {
            logger.info(s"$container looks alive...")
            container
          }

        case HealthCheckResult.WithPorts(ports) =>
          val out = container.copy(availablePorts = ports)
          F.maybeSuspend {
            logger.info(s"${out -> "container"} looks good...")
            out
          }

        case HealthCheckResult.Failed(t) =>
          F.fail(new RuntimeException(s"Container failed: ${container.id}", t))

        case HealthCheckResult.Unknown =>
          P.sleep(config.healthCheckInterval).flatMap(_ => await(container))
      }
    }

    private[this] def runReused(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
      logger.info(s"Running container with reused option with ${config.pullTimeout}.")
      FileLockMutex.withLocalMutex(
        s"${config.image.replace("/", "_")}:${config.ports.mkString(";")}",
        logger,
        1.second,
        config.pullTimeout.toSeconds.toInt
      ) {
        for {
          containers <- F.maybeSuspend {
            // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
            try {
              client
                .listContainersCmd()
                .withAncestorFilter(List(config.image).asJava)
                .withStatusFilter(List("running").asJava)
                .exec()
            } catch {
              case c: Throwable =>
                throw new IntegrationCheckException(Seq(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c))))
            }
          }.map(_.asScala.toList.sortBy(_.getId))

          candidates = {
            val portSet = ports.map(_.port).toSet
            containers.iterator.flatMap {
              c =>
                val inspection = client.inspectContainerCmd(c.getId).exec()
                mapContainerPorts(inspection) match {
                  case Left(value) =>
                    logger.info(s"Container ${c.getId} missing ports $value so will not be reused")
                    Seq.empty
                  case Right(value) =>
                    Seq((c, inspection, value))
                }
            }.find {
              case (_, _, eports) =>
                portSet.diff(eports.keySet).isEmpty
            }
          }
          existing <- candidates match {
            case Some((c, inspection, existingPorts)) =>
              val unverified = DockerContainer[T](
                id = ContainerId(c.getId),
                name = inspection.getName,
                ports = existingPorts,
                containerConfig = config,
                clientConfig = clientw.clientConfig,
                availablePorts = Map.empty,
                hostName = inspection.getConfig.getHostName
              )
              await(unverified)
            case None =>
              doRun(ports)
          }
        } yield existing
      }
    }

    private[this] def doRun(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
      val allPortLabels = ports.flatMap(p => p.labels).toMap
      val baseCmd = client
        .createContainerCmd(config.image)
        .withLabels((clientw.labels ++ allPortLabels).asJava)

      val volumes = config.mounts.map {
        case Docker.Mount(h, c, true) => new Bind(h, new Volume(c), true)
        case Docker.Mount(h, c, _) => new Bind(h, new Volume(c))
      }

      for {
        out <- F.maybeSuspend {
          @silent("method.*Bind.*deprecated")
          val cmd = Value(baseCmd)
            .mut(config.name) { case (n, c) => c.withName(n) }
            .mut(ports.nonEmpty)(_.withExposedPorts(ports.map(_.binding.getExposedPort).asJava))
            .mut(ports.nonEmpty)(_.withPortBindings(ports.map(_.binding).asJava))
            .mut(config.env.nonEmpty)(_.withEnv(config.env.map {
              case (k, v) => s"$k=$v"
            }.toList.asJava))
            .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
            .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.cmd.toList.asJava))
            .mut(config.cwd)((cwd, cmd) => cmd.withWorkingDir(cwd))
            .mut(config.user)((user, cmd) => cmd.withUser(user))
            .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
            .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
            .get

          logger.info(s"Going to pull `${config.image}`...")
          client
            .pullImageCmd(config.image)
            .start()
            .awaitCompletion(config.pullTimeout.toMillis, TimeUnit.MILLISECONDS);

          logger.debug(s"Going to create container from image `${config.image}`...")
          val res = cmd.exec()

          logger.debug(s"Going to start container ${res.getId -> "id"}...")
          client.startContainerCmd(res.getId).exec()

          val inspection = client.inspectContainerCmd(res.getId).exec()
          val hostName = inspection.getConfig.getHostName
          val maybeMappedPorts = mapContainerPorts(inspection)

          maybeMappedPorts match {
            case Left(value) =>
              throw new RuntimeException(s"Created container from `${config.image}` with ${res.getId -> "id"}, but ports are missing: $value!")

            case Right(mappedPorts) =>
              val container = DockerContainer[T](ContainerId(res.getId), inspection.getName, hostName, mappedPorts, config, clientw.clientConfig, Map.empty)
              logger.debug(s"Created $container from ${config.image}...")
              logger.debug(s"Going to attach container ${res.getId -> "id"} to ${config.networks -> "networks"}")
              config.networks.foreach {
                network =>
                  client
                    .connectToNetworkCmd()
                    .withContainerId(container.id.name)
                    .withNetworkId(network.id)
                    .exec()
              }

              container
          }
        }
        result <- await(out)
      } yield result
    }

    private def mapContainerPorts(inspection: InspectContainerResponse): Either[Seq[DockerPort], Map[DockerPort, Seq[ServicePort]]] = {
      val network = inspection.getNetworkSettings
      val networkAddresses = network.getNetworks.asScala.values.toList

      val ports = config.ports.map {
        containerPort =>
          val mappings = for {
            exposed <- Option(network.getPorts.getBindings.get(toExposedPort(containerPort))).toSeq
            port <- exposed
          } yield port
          (containerPort, mappings)
      }

      val (good, bad) = ports.partition(_._2.nonEmpty)
      if (bad.isEmpty) {
        Right(good.map {
          case (containerPort, ports) =>
            containerPort -> ports.map(port => ServicePort(networkAddresses.map(_.getIpAddress), port.getHostIp, Integer.parseInt(port.getHostPortSpec)))
        }.toMap)
      } else {
        Left(bad.map(_._1))
      }

    }
  }

}
