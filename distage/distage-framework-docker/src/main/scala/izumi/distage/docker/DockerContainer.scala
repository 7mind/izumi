package izumi.distage.docker

import java.util.concurrent.TimeUnit

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.PullImageResultCallback
import distage.TagK
import izumi.distage.docker.Docker.{AvailablePort, ClientConfig, ContainerConfig, ContainerId, DockerPort, HealthCheckResult, Mount, ServicePort}
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.functional.Value
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.network.IzSockets
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._

trait ContainerDef {
  type Tag
  type Container = DockerContainer[Tag]
  type Config = ContainerConfig[Tag]

  def config: Config

  /** For binding in `ModuleDef`:
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
    * To kill all the containers: `docker rm -f $(docker ps -q -a -f 'label=distage.type')`
    *
    */
  final def make[F[_]: TagK](implicit tag: distage.Tag[Container]): ProviderMagnet[DIResource[F, Container]] = {
    tag.discard()
    DockerContainer.resource[F](this)
  }
}

object ContainerDef {
  type Aux[T] = ContainerDef {type Tag = T}
}

final case class DockerContainer[Tag](
                                       id: Docker.ContainerId,
                                       name: String,
                                       ports: Map[Docker.DockerPort, Seq[ServicePort]],
                                       containerConfig: ContainerConfig[Tag],
                                       clientConfig: ClientConfig,
                                       availablePorts: Map[Docker.DockerPort, Seq[AvailablePort]],
                                     ) {
  override def toString: String = s"$name:${id.name} ports=${ports.mkString("{", ", ", "}")} available=${availablePorts.mkString("{", ", ", "}")}"
}

object DockerContainer {
  def resource[F[_]](conf: ContainerDef): (DockerClientWrapper[F], IzLogger, DIEffect[F], DIEffectAsync[F]) => DIResource[F, DockerContainer[conf.Tag]] = {
    new Resource[F, conf.Tag](conf)(_, _)(_, _)
  }

  implicit final class DockerProviderExtensions[F[_], T](private val self: ProviderMagnet[DIResource[F, DockerContainer[T]]]) extends AnyVal {
    def dependOnDocker(containerDecl: ContainerDef)(implicit tag: distage.Tag[DockerContainer[containerDecl.Tag]]): ProviderMagnet[DIResource[F, DockerContainer[T]]] = {
      self.addDependency[DockerContainer[containerDecl.Tag]]
    }

    def dependOnDocker[T2](implicit tag: distage.Tag[DockerContainer[T2]]): ProviderMagnet[DIResource[F, DockerContainer[T]]] = {
      self.addDependency[DockerContainer[T2]]
    }
  }

  private[this] final case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])

  final class Resource[F[_]: DIEffect: DIEffectAsync, T]
  (
    containerDecl: ContainerDef.Aux[T],
  )(
    clientw: DockerClientWrapper[F],
    logger: IzLogger,
  ) extends DIResource[F, DockerContainer[T]] {

    private[this] val client = clientw.client
    private[this] val config = containerDecl.config

    implicit class DockerPortEx(port: DockerPort) {
      def toExposedPort: ExposedPort = {
        port match {
          case DockerPort.TCP(number) =>
            ExposedPort.tcp(number)
          case DockerPort.UDP(number) =>
            ExposedPort.udp(number)
        }
      }
    }

    override def acquire: F[DockerContainer[T]] = {
      val ports = config.ports.map {
        containerPort =>
          val local = IzSockets.temporaryLocalPort()
          val bp = containerPort.toExposedPort
          val binding = new PortBinding(Ports.Binding.bindPort(local), bp)
          val stableLabels = Map(
            "distage.reuse" -> config.reuse.toString,
            s"distage.port.${containerPort.protocol}.${containerPort.number}.defined" -> "true",
          )
          val labels = Map(
            s"distage.port.${containerPort.protocol}.${containerPort.number}" -> local.toString,
          )
          PortDecl(containerPort, local, binding, stableLabels ++ labels)
      }

      if (config.reuse && clientw.clientConfig.allowReuse) {
        for {
          containers <- DIEffect[F]
            .maybeSuspend {
              // FIXME: temporary hack to allow missing containers to skip tests
              try {
                client.listContainersCmd()
                  .withAncestorFilter(List(config.image).asJava)
                  .withStatusFilter(List("running").asJava)
                  .exec()
              } catch {
                case c: java.net.ConnectException =>
                  throw new IntegrationCheckException(Seq(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c))))
              }
            }
            .map(_.asScala.toList.sortBy(_.getId))

          candidates = containers.view.flatMap {
            c =>
              val inspection = client.inspectContainerCmd(c.getId).exec()
              mapContainerPorts(inspection) match {
                case Left(value) =>
                  logger.info(s"Container ${c.getId} missing ports $value so will not be reused")
                  Seq.empty
                case Right(value) =>
                  Seq((c, inspection, value))
              }
          }
            .find {
              case (_, _, eports) => ports.map(_.port).toSet.diff(eports.keySet).isEmpty
            }
          existing <- candidates match {
            case Some((c, inspection, existingPorts)) =>
              for {
                unverified <- DIEffect[F].pure(DockerContainer[T](ContainerId(c.getId), inspection.getName, existingPorts, config, clientw.clientConfig, Map.empty))
                container <- await(unverified)
              } yield {
                container
              }
            case None =>
              doRun(ports)
          }
        } yield {
          existing
        }
      } else {
        doRun(ports)
      }
    }

    def await(container: DockerContainer[T]): F[DockerContainer[T]] = {
      DIEffect[F].maybeSuspend {
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
          logger.info(s"$container looks alive...")
          DIEffect[F].pure(container)
        case HealthCheckResult.WithPorts(ports) =>
          val out = container.copy(availablePorts = ports)
          logger.info(s"${out -> "container"} looks good...")
        DIEffect[F].pure(out)
        case HealthCheckResult.Failed(t) =>
          DIEffect[F].fail(new RuntimeException(s"Container failed: ${container.id}", t))
        case HealthCheckResult.Uknnown =>
          DIEffectAsync[F].sleep(config.healthCheckInterval).flatMap(_ => await(container))
      }
    }

    private[this] def doRun(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
      val allPortLabels = ports.flatMap(p => p.labels).toMap
      val baseCmd = client
        client.cmd
        .createContainerCmd(config.image)
        .withLabels((clientw.labels ++ allPortLabels).asJava)

      val volumes = config.mounts.map {
        case Mount(h, c, true) => new Bind(h, new Volume(c), true)
        case Mount(h, c, _) => new Bind(h, new Volume(c))
      }

      for {
        out <- DIEffect[F].maybeSuspend {
          val cmd = Value(baseCmd)
            .mut(config.name){case (n, c) => c.withName(n)}
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
            .exec(new PullImageResultCallback())
            .awaitCompletion(config.pullTimeout.toMillis, TimeUnit.MILLISECONDS);

          logger.debug(s"Going to create container from image `${config.image}`...")
          val res = cmd.exec()

          logger.debug(s"Going to start container ${res.getId -> "id"}...")
          client.startContainerCmd(res.getId).exec()

          val inspection = client.inspectContainerCmd(res.getId).exec()
          val maybeMappedPorts = mapContainerPorts(inspection)

          maybeMappedPorts match {
            case Left(value) =>
              throw new RuntimeException(s"Created container from `${config.image}` with ${res.getId -> "id"}, but ports are missing: $value!")
            case Right(mappedPorts) =>
              val container = DockerContainer[T](ContainerId(res.getId), inspection.getName, mappedPorts, config, clientw.clientConfig, Map.empty)
              logger.debug(s"Created $container from ${config.image}...")

              if (config.networks.nonEmpty) {
                logger.debug(s"Going to attach container ${res.getId -> "id"} to ${config.networks -> "networks"}")

                val existedNetworks: List[Network] = client.listNetworksCmd().exec().asScala.toList

                config.networks.map {
                  network =>
                    existedNetworks.find(_.getName == network.name).fold {
                      logger.debug(s"Going to create $network ...")
                      client.createNetworkCmd().withName(network.name).exec().getId
                    }(_.getId)
                }.foreach {
                  client.connectToNetworkCmd()
                    .withContainerId(container.id.name)
                    .withNetworkId(_)
                    .exec()
                }
              }

              container
          }
        }
        result <- await(out)
      } yield {
        result
      }
    }

    override def release(resource: DockerContainer[T]): F[Unit] = {
      if (!resource.containerConfig.reuse) {
        clientw.destroyContainer(resource.id)
      } else {
        DIEffect[F].unit
      }
    }

    private def mapContainerPorts(inspection: InspectContainerResponse): Either[Seq[DockerPort], Map[DockerPort, Seq[ServicePort]]] = {
      val network = inspection.getNetworkSettings
      val networkAddresses = network.getNetworks.asScala.values.toList

      val ports = config.ports.map {
        containerPort =>
          val mappings = for {
            exposed <- Option(network.getPorts.getBindings.get(containerPort.toExposedPort)).toSeq
            port <- exposed
          } yield {
            port
          }
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
