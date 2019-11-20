package izumi.distage.testkit.integration.docker

import java.util.concurrent.TimeUnit

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.PullImageResultCallback
import distage.TagK
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.integration.docker.Docker.{ClientConfig, ContainerConfig, ContainerId, DockerPort, HealthCheckResult, ServicePort}
import izumi.functional.Value
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
  final def make[F[_] : TagK](implicit tag: distage.Tag[Tag]): ProviderMagnet[DIResource[F, DockerContainer[Tag]]] = {
    tag.discard()
    DockerContainer.resource[F](this)
  }
}

object ContainerDef {
  type Aux[T] = ContainerDef {type Tag = T}
}

case class DockerContainer[Tag](
                                 id: Docker.ContainerId,
                                 mapping: Map[Docker.DockerPort, ServicePort],
                                 containerConfig: ContainerConfig[Tag],
                                 clientConfig: ClientConfig,
                               )

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

  class Resource[F[_] : DIEffect : DIEffectAsync, T]
  (
    containerDecl: ContainerDef.Aux[T],
  )(
    clientw: DockerClientWrapper[F],
    logger: IzLogger,
  ) extends DIResource[F, DockerContainer[T]] {
    private val client = clientw.client
    private val config = containerDecl.config

    implicit class DockerPortEx(port: DockerPort) {
      def toExposedPort: ExposedPort = port match {
        case DockerPort.TCP(number) =>
          ExposedPort.tcp(number)
        case DockerPort.UDP(number) =>
          ExposedPort.udp(number)
      }
    }

    case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])

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
            .maybeSuspend(
              client.listContainersCmd()
                .withAncestorFilter(List(config.image).asJava)
                .withStatusFilter(List("running").asJava)
                .exec()
            )
            .map(_.asScala.toList.sortBy(_.getId))
          existing <- containers.view
            .map(c => (c, mapPorts(c.getId).toMap))
            .find { case (_, eports) => ports.map(_.port).toSet.diff(eports.keySet).isEmpty } match {
            case Some((c, existingPorts)) =>
              logger.info(s"Reusing running container ${c.getId} with $existingPorts...")
              DIEffect[F].pure(DockerContainer[T](ContainerId(c.getId), existingPorts, config, clientw.clientConfig))
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

    private def mapPorts(containerId: String): Seq[(DockerPort, ServicePort)] = {
      val inspection = client.inspectContainerCmd(containerId).exec()
      val existingPorts = mapContainerPorts(inspection)
      existingPorts
    }

    private def doRun(ports: Seq[PortDecl]) = {
      val allPortLabels = ports.flatMap(p => p.labels).toMap
      val baseCmd = client
        .createContainerCmd(config.image)
        .withLabels((clientw.labels ++ allPortLabels).asJava)

      val volumes = config.mounts.map(m => new Bind(m.host, new Volume(m.container), true))

      def await(c: DockerContainer[T]): F[Unit] = {
        DIEffect[F].maybeSuspend {
          try {
            val status = client.inspectContainerCmd(c.id.name).exec()
            if (status.getState.getRunning) {
              config.healthCheck.check(c)
            } else {
              HealthCheckResult.Failed(new RuntimeException(s"Container exited: ${c.id}, full status: $status"))
            }
          } catch {
            case t: Throwable =>
              HealthCheckResult.Failed(t)
          }
        }.flatMap {
          case HealthCheckResult.Running =>
            DIEffect[F].unit
          case HealthCheckResult.Failed(t) =>
            DIEffect[F].fail(new RuntimeException(s"Container failed: ${c.id}", t))
          case HealthCheckResult.Uknnown =>
            DIEffectAsync[F].sleep(config.healthCheckInterval).flatMap(_ => await(c))
        }
      }

      for {
        out <- DIEffect[F].maybeSuspend {
          val cmd = Value(baseCmd)
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

          val mappedPorts = mapPorts(res.getId)
          logger.info(s"Created container from `${config.image}` with ${res.getId -> "id"}, $mappedPorts...")

          DockerContainer[T](ContainerId(res.getId), mappedPorts.toMap, config, clientw.clientConfig)
        }
        _ <- await(out)
      } yield out
    }

    override def release(resource: DockerContainer[T]): F[Unit] = {
      if (!resource.containerConfig.reuse) {
        clientw.destroyContainer(resource.id)
      } else {
        DIEffect[F].unit
      }
    }

    private def mapContainerPorts(inspection: InspectContainerResponse): Seq[(DockerPort, ServicePort)] = {
      config.ports.map {
        containerPort =>
          val network = inspection.getNetworkSettings
          val port = Option(network.getPorts.getBindings.get(containerPort.toExposedPort))
            .flatMap(_.headOption)
            .getOrElse(throw new RuntimeException(s"Missing $containerPort on container ${inspection.getId}"))

          val networkAddresses = network.getNetworks.asScala.values.toList
          containerPort -> ServicePort(networkAddresses.map(_.getIpAddress), port.getHostIp, Integer.parseInt(port.getHostPortSpec))
      }
    }
  }

}
