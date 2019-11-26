package izumi.distage.testkit.integration.docker

import java.util.UUID

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientBuilder}
import izumi.distage.model.definition.DIResource
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.testkit.integration.docker.Docker.{ClientConfig, ContainerId}
import izumi.functional.Value
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters._

class DockerClientWrapper[F[_]]
(
  val client: DockerClient,
  val clientConfig: ClientConfig,
  val labelsBase: Map[String, String],
  val labelsUnique: Map[String, String],
  logger: IzLogger,
)(
  implicit
  F: DIEffect[F],
) {
  def labels: Map[String, String] = labelsBase ++ labelsUnique

  def destroyContainer(container: ContainerId): F[Unit] = {
    F.maybeSuspend {
      logger.info(s"Going to destroy $container...")

      try {
        client
          .stopContainerCmd(container.name)
          .exec()
          .discard()
      } finally {
        client
          .removeContainerCmd(container.name)
          .withForce(true)
          .exec()
          .discard()
      }
    }
  }
}

object DockerClientWrapper {

  class Resource[F[_] : DIEffect : DIEffectAsync]
  (
    factory: DockerCmdExecFactory,
    logger: IzLogger,
    clientConfig: ClientConfig,
  ) extends DIResource[F, DockerClientWrapper[F]]
    with IntegrationCheck {

    private[this] lazy val dcc = Value(DefaultDockerClientConfig.createDefaultConfigBuilder())
      .mut(clientConfig.remote.filter(_ => clientConfig.useRemote))((c, b) => b.withDockerHost(c.host).withDockerTlsVerify(c.tlsVerify).withDockerCertPath(c.certPath).withDockerConfig(c.config))
      .mut(clientConfig.registry.filter(_ => clientConfig.useRegistry))((c, b) => b.withRegistryUrl(c.url).withRegistryUsername(c.username).withRegistryPassword(c.password).withRegistryEmail(c.email))
      .get.build()

    private[this] lazy val client = DockerClientBuilder
      .getInstance(dcc)
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
          client = client,
          labelsBase = Map("distage.type" -> "testkit"),
          labelsUnique = Map("distage.run" -> UUID.randomUUID().toString),
          logger = logger,
          clientConfig = clientConfig,
        )
      }
    }

    override def release(resource: DockerClientWrapper[F]): F[Unit] = {
      for {
        containers <- DIEffect[F].maybeSuspend(resource.client.listContainersCmd().withLabelFilter(resource.labels.asJava).exec())
        _ <- DIEffect[F].traverse_(containers.asScala.filterNot(_.getLabels.getOrDefault("distage.reuse", "false") == "true"))(c => resource.destroyContainer(ContainerId(c.getId)))
        _ <- DIEffect[F].maybeSuspend(resource.client.close())
      } yield ()
    }
  }

}
