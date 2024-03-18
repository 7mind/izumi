package izumi.distage.docker.healthcheck

import izumi.distage.docker.model.Docker.ContainerState
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.model.Docker
import izumi.logstage.api.IzLogger

final class ExitSuccessCheck(exitCode: Int) extends ContainerHealthCheck {
  override def check(logger: IzLogger, container: DockerContainer[?], state: Docker.ContainerState): HealthCheckResult = {
    state match {
      case ContainerState.Exited(code) if code == exitCode =>
        logger.info(s"$container successfully exited, health check passed.")
        HealthCheckResult.Passed

      case ContainerState.Exited(status) =>
        HealthCheckResult.Terminated(s"Container terminated with unexpected code. Code=$status, expected=$exitCode")

      case ContainerState.Running =>
        logger.info(s"$container is still running, expected container to exit, health check failed.")
        HealthCheckResult.Failed("Container is still running, expected container to exit")

      case ContainerState.NotFound =>
        HealthCheckResult.Terminated("Container not found, expected container to exit with status code 0.")

    }
  }
}
