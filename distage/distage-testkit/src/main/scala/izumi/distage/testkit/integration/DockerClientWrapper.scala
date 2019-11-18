package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.model.IntegrationCheck
import izumi.distage.testkit.integration.Docker.ContainerId
import izumi.fundamentals.platform.integration.ResourceCheck

class DockerClientWrapper[F[_] : DIEffect](val client: DockerClient, val labels: Map[String, String]) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = {
    try {
      client.infoCmd().exec()
      ResourceCheck.Success()
    } catch {
      case t: Throwable =>
        ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
    }
  }

  def destroyContainer(id: ContainerId): F[Unit] = {
    DIEffect[F].maybeSuspend {
      try {
        client
          .stopContainerCmd(id.name)
          .exec()
        ()
      } finally {
        client
          .removeContainerCmd(id.name)
          .withForce(true)
          .exec()
        ()
      }
    }
  }
}
