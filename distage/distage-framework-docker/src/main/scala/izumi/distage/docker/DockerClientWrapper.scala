package izumi.distage.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import izumi.distage.docker.Docker.{ClientConfig, ContainerId}
import izumi.distage.docker.DockerClientWrapper.{ContainerDestroyMeta, RemovalReason}
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax._
import izumi.functional.Value
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

import java.util.UUID
import scala.jdk.CollectionConverters._

class DockerClientWrapper[F[_]](
  val rawClient: DockerClient,
  val rawClientConfig: DockerClientConfig,
  val clientConfig: ClientConfig,
  val labelsBase: Map[String, String],
  val labelsJvm: Map[String, String],
  val labelsUnique: Map[String, String],
  logger: IzLogger,
)(implicit
  F: QuasiIO[F]
) {
  def labels: Map[String, String] = labelsBase ++ labelsJvm ++ labelsUnique

  def removeContainer(containerId: ContainerId, context: ContainerDestroyMeta, removalReason: RemovalReason): F[Unit] = {
    F.maybeSuspend {
      try {
        logger.info(s"Going to remove $containerId $removalReason ($context)...")

        try {
          rawClient
            .stopContainerCmd(containerId.name)
            .exec()
        } catch {
          case _: NotModifiedException =>
        } finally {
          rawClient
            .removeContainerCmd(containerId.name)
            .withForce(true)
            .exec()
            .discard()
        }

        logger.info(s"Removed $containerId ($context)")
      } catch {
        case failure: Throwable =>
          logger.warn(s"Got failure during container remove $containerId ${failure.stackTrace -> "failure"}")
      }
    }
  }
}

object DockerClientWrapper {
  private[this] val jvmRun: String = UUID.randomUUID().toString

  sealed trait ContainerDestroyMeta
  object ContainerDestroyMeta {
    final case class ParameterizedContainer[+Tag](container: DockerContainer[Tag]) extends ContainerDestroyMeta {
      override def toString: String = container.toString
    }
    final case class RawContainer(container: Container) extends ContainerDestroyMeta {
      override def toString: String = container.toString
    }
  }

  sealed trait RemovalReason
  object RemovalReason {
    case object NotReused extends RemovalReason
    case object NotReusedAndYetWasNotCleanedUpEarlierByItsFinalizer extends RemovalReason
    case object AlreadyExited extends RemovalReason
  }

  final class Resource[F[_]](
    logger: IzLogger,
    clientConfig: ClientConfig,
    clientFactory: DockerClientFactory,
  )(implicit
    F: QuasiIO[F]
  ) extends Lifecycle.Basic[F, DockerClientWrapper[F]]
    with IntegrationCheck[F] {

    private[this] lazy val rawClientConfig: DefaultDockerClientConfig = {
      Value(DefaultDockerClientConfig.createDefaultConfigBuilder())
        .mut(clientConfig.remote.filter(_ => clientConfig.useRemote))(
          (b, c) => b.withDockerHost(c.host).withDockerTlsVerify(c.tlsVerify).withDockerCertPath(c.certPath).withDockerConfig(c.config)
        )
        .mut(clientConfig.registry.filter(_ => clientConfig.useRegistry))(
          (b, c) => b.withRegistryUrl(c.url).withRegistryUsername(c.username).withRegistryPassword(c.password).withRegistryEmail(c.email)
        )
        .get.build()
    }

    private[this] lazy val rawClient: DockerClient = {
      clientFactory.makeClient(clientConfig, rawClientConfig)
    }

    override def resourcesAvailable(): F[ResourceCheck] = F.maybeSuspend {
      try {
        rawClient.infoCmd().exec()
        ResourceCheck.Success()
      } catch {
        case t: Throwable =>
          ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
      }
    }

    override def acquire: F[DockerClientWrapper[F]] = {
      for {
        runId <- F.maybeSuspend(UUID.randomUUID().toString)
      } yield {
        new DockerClientWrapper[F](
          rawClient = rawClient,
          rawClientConfig = rawClientConfig,
          labelsBase = Map(DockerConst.Labels.containerTypeLabel -> "testkit"),
          labelsJvm = Map(DockerConst.Labels.jvmRunId -> jvmRun),
          labelsUnique = Map(DockerConst.Labels.distageRunId -> runId),
          logger = logger,
          clientConfig = clientConfig,
        )
      }
    }

    override def release(resource: DockerClientWrapper[F]): F[Unit] = {
      (for {
        containers <- F.maybeSuspend {
          resource.rawClient
            .listContainersCmd()
            .withStatusFilter(List(DockerConst.State.exited, DockerConst.State.running).asJava)
            .withLabelFilter(resource.labels.asJava)
            .exec()
        }
        _ <- F.traverse_(containers.asScala) {
          (c: Container) =>
            // destroy all containers that should not be reused or that exited (to not accumulate containers that could be pruned)
            val notReused = Option(c.getLabels.get(DockerConst.Labels.reuseLabel)).forall(_.asBoolean().contains(false))
            val removalReason = if (notReused) {
              Some(RemovalReason.NotReusedAndYetWasNotCleanedUpEarlierByItsFinalizer)
            } else if (c.getState == DockerConst.State.exited) {
              Some(RemovalReason.AlreadyExited)
            } else {
              None
            }
            F.traverse_(removalReason) {
              resource.removeContainer(ContainerId(c.getId), ContainerDestroyMeta.RawContainer(c), _)
            }
        }
      } yield ())
        .guarantee(F.maybeSuspend(resource.rawClient.close()))
    }
  }

}
