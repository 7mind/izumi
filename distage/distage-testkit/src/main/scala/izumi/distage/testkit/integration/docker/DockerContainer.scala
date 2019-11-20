package izumi.distage.testkit.integration.docker

import java.util.concurrent.TimeUnit

import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.PullImageResultCallback
import distage.TagK
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.testkit.integration.docker.Docker.{ContainerConfig, ContainerId, DockerPort, HealthCheckResult}
import izumi.functional.Value
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.network.IzSockets
import izumi.logstage.api.IzLogger

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
    */
  final def make[F[_]: TagK](implicit tag: distage.Tag[Tag]): ProviderMagnet[DIResource[F, DockerContainer[Tag]]] = {
    tag.discard()
    DockerContainer.resource[F](this)
  }
}
object ContainerDef {
  type Aux[T] = ContainerDef {type Tag = T}
}

case class DockerContainer[Tag](
                                 id: Docker.ContainerId,
                                 mapping: Map[Docker.DockerPort, Int],
                                 containerConfig: ContainerConfig[Tag],
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

  class Resource[F[_]: DIEffect: DIEffectAsync, T]
  (
    containerDecl: ContainerDef.Aux[T],
  )(
    clientw: DockerClientWrapper[F],
    logger: IzLogger,
  ) extends DIResource[F, DockerContainer[T]] {
    private val client = clientw.client
    private val config = containerDecl.config

    override def acquire: F[DockerContainer[T]] = {
      val ports = config.ports.map(_ -> IzSockets.temporaryLocalPort())

      val specs = ports.map {
        case (container, local) =>

          val p = container match {
            case DockerPort.TCP(number) =>
              ExposedPort.tcp(number)
          }
          new PortBinding(Ports.Binding.bindPort(local), p)
      }

      val exposed = config.ports.map {
        case DockerPort.TCP(number) =>
          ExposedPort.tcp(number)
      }
      import scala.jdk.CollectionConverters._

      val baseCmd = client
        .createContainerCmd(config.image)
        .withLabels(clientw.labels.asJava)

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
            .mut(exposed.nonEmpty)(_.withExposedPorts(exposed: _*))
            .mut(specs.nonEmpty)(_.withPortBindings(specs: _*))
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
          logger.info(s"Created container from `${config.image}` with ${res.getId -> "id"} ...")

          client.startContainerCmd(res.getId).exec()

          DockerContainer[T](ContainerId(res.getId), ports.toMap, config)
        }
        _ <- await(out)
      } yield out
    }

    override def release(resource: DockerContainer[T]): F[Unit] = {
      clientw.destroyContainer(resource.id)
    }
  }
}
