package izumi.distage.docker

import java.util.UUID

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder, DockerClientConfig}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.docker.Docker.{ClientConfig, ContainerId}
import izumi.distage.framework.model.IntegrationCheck
import izumi.functional.Value
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._

class DockerClientWrapper[F[_]](
  val rawClient: DockerClient,
  val rawClientConfig: DockerClientConfig,
  val clientConfig: ClientConfig,
  val labelsBase: Map[String, String],
  val labelsUnique: Map[String, String],
  logger: IzLogger,
)(implicit
  F: DIEffect[F]
) {
  def labels: Map[String, String] = labelsBase ++ labelsUnique

  def destroyContainer(container: ContainerId): F[Unit] = {
    F.definitelyRecover {
      F.maybeSuspend {
        logger.info(s"Going to destroy $container...")

        try {
          rawClient
            .stopContainerCmd(container.name)
            .exec()
            .discard()
        } finally {
          rawClient
            .removeContainerCmd(container.name)
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
          labelsUnique = Map("distage.run" -> UUID.randomUUID().toString),
          logger = logger,
          clientConfig = clientConfig,
        )
      }
    }

    override def release(resource: DockerClientWrapper[F]): F[Unit] = {
      for {
        containers <- DIEffect[F].maybeSuspend(resource.rawClient.listContainersCmd().withLabelFilter(resource.labels.asJava).exec())
        // destroy all containers that should not be reused, or was exited (to not to cumulate containers that could be pruned)
        containersToDestroy = containers.asScala.filterNot {
          c =>
            c.getLabels.getOrDefault("distage.reuse", "false") == "true" || c.getStatus == "exited"
        }
        _ <- DIEffect[F].traverse_(containersToDestroy)(c => resource.destroyContainer(ContainerId(c.getId)))
        _ <- DIEffect[F].maybeSuspend(resource.rawClient.close())
      } yield ()
    }
  }

}
