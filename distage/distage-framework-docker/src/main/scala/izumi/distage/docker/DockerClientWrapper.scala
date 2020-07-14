package izumi.distage.docker

import java.util.UUID

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder, DockerClientConfig}
import izumi.distage.docker.Docker.{ClientConfig, ContainerId}
import izumi.distage.docker.DockerClientWrapper.ContainerDestroyMeta
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.functional.Value
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._
import com.github.dockerjava.api.model.Container
import scala.annotation.nowarn

class DockerClientWrapper[F[_]](
  val rawClient: DockerClient,
  val rawClientConfig: DockerClientConfig,
  val clientConfig: ClientConfig,
  val labelsBase: Map[String, String],
  val labelsJvm: Map[String, String],
  val labelsUnique: Map[String, String],
  logger: IzLogger,
)(implicit
  F: DIEffect[F]
) {
  def labels: Map[String, String] = labelsBase ++ labelsJvm ++ labelsUnique

  def destroyContainer(containerId: ContainerId, context: ContainerDestroyMeta): F[Unit] = {
    F.definitelyRecover {
      F.maybeSuspend {
        logger.info(s"Going to destroy $containerId ($context)...")

        try {
          rawClient
            .stopContainerCmd(containerId.name)
            .exec()
            .discard()
        } finally {
          rawClient
            .removeContainerCmd(containerId.name)
            .withForce(true)
            .exec()
            .discard()
        }
      }
    } {
      e => F.maybeSuspend(logger.warn(s"Got failure during container destroy ${e.getMessage -> "message"}"))
    }
  }
}

object DockerClientWrapper {
  sealed trait ContainerDestroyMeta
  object ContainerDestroyMeta {
    final case class ParameterizedContainer[T](container: DockerContainer[T]) extends ContainerDestroyMeta {
      override def toString: String = container.toString
    }
    final case class RawContainer(container: Container) extends ContainerDestroyMeta {
      override def toString: String = container.toString
    }
  }

  private[this] val jvmRun: String = UUID.randomUUID().toString

  class Resource[F[_]: DIEffect](
    factory: DockerCmdExecFactory,
    logger: IzLogger,
    clientConfig: ClientConfig,
  ) extends DIResource[F, DockerClientWrapper[F]]
    with IntegrationCheck {

    private[this] lazy val rawClientConfig = Value(DefaultDockerClientConfig.createDefaultConfigBuilder())
      .mut(clientConfig.remote.filter(_ => clientConfig.useRemote))(
        (c, b) => b.withDockerHost(c.host).withDockerTlsVerify(c.tlsVerify).withDockerCertPath(c.certPath).withDockerConfig(c.config)
      )
      .mut(clientConfig.registry.filter(_ => clientConfig.useRegistry))(
        (c, b) => b.withRegistryUrl(c.url).withRegistryUsername(c.username).withRegistryPassword(c.password).withRegistryEmail(c.email)
      )
      .get.build()

    @nowarn("msg=deprecated")
    private[this] lazy val client = DockerClientBuilder
      .getInstance(rawClientConfig)
      .withDockerCmdExecFactory(factory)
      .build

    override def resourcesAvailable(): ResourceCheck = {
      try {
        client.infoCmd().exec()
        ResourceCheck.Success()
      } catch {
        case t: Throwable =>
          ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
      }
    }

    override def acquire: F[DockerClientWrapper[F]] = {
      DIEffect[F].maybeSuspend {
        new DockerClientWrapper[F](
          rawClient = client,
          rawClientConfig = rawClientConfig,
          labelsBase = Map("distage.type" -> "testkit"),
          labelsJvm = Map("distage.jvmrun" -> jvmRun),
          labelsUnique = Map("distage.run" -> UUID.randomUUID().toString),
          logger = logger,
          clientConfig = clientConfig,
        )
      }
    }

    override def release(resource: DockerClientWrapper[F]): F[Unit] = {
      for {
        containers <- DIEffect[F].maybeSuspend {
          resource
            .rawClient
            .listContainersCmd()
            .withStatusFilter(List("running", "exited").asJava)
            .withLabelFilter(resource.labels.asJava)
            .exec()
        }
        // destroy all containers that should not be reused, or was exited (to not to accumulate containers that could be pruned)
        containersToDestroy = containers.asScala.filter {
          c =>
            import izumi.fundamentals.platform.strings.IzString._
            Option(c.getLabels.get(ContainerResource.reuseLabel)).forall(label => label.asBoolean().contains(false)) || c.getState == "exited"
        }
        _ <- DIEffect[F].traverse_(containersToDestroy) {
          c: Container =>
            val id = ContainerId(c.getId)
            DIEffect[F].definitelyRecover(resource.destroyContainer(id, ContainerDestroyMeta.RawContainer(c))) {
              error =>
                DIEffect[F].maybeSuspend(logger.warn(s"Failed to destroy container $id: $error"))
            }

        }
        _ <- DIEffect[F].maybeSuspend(resource.rawClient.close())
      } yield ()
    }
  }

}
