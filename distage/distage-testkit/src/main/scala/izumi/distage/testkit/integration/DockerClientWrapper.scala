package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.testkit.integration.Docker.ContainerId
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.logstage.api.IzLogger

class DockerClientWrapper[F[_]](
                                 val client: DockerClient,
                                 val labels: Map[String, String],
                                 val logger: IzLogger,
                               )(
                                 implicit val eff: DIEffect[F],
                                 val effa: DIEffectAsync[F]
                               ) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = {
    try {
      client.infoCmd().exec()
      ResourceCheck.Success()
    } catch {
      case t: Throwable =>
        ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
    }
  }

  def destroyContainer(container: ContainerId): F[Unit] = {
    logger.info(s"Going to destroy $container...")
    eff.maybeSuspend {
      try {
        client
          .stopContainerCmd(container.name)
          .exec()
        ()
      } finally {
        client
          .removeContainerCmd(container.name)
          .withForce(true)
          .exec()
        ()
      }
    }
  }
}
