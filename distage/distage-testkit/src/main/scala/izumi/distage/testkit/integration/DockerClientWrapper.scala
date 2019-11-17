package izumi.distage.testkit.integration

import com.github.dockerjava.api.DockerClient
import izumi.distage.roles.model.IntegrationCheck
import izumi.fundamentals.platform.integration.ResourceCheck

class DockerClientWrapper(val client: DockerClient) extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = {
    try {
      client.infoCmd().exec()
      ResourceCheck.Success()
    } catch {
      case t: Throwable =>
        ResourceCheck.ResourceUnavailable("Docker daemon is unavailable", Some(t))
    }

  }
}
