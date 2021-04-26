package izumi.distage.docker.healthcheck
import izumi.distage.docker.Docker.ContainerState
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.{Docker, DockerContainer}
import izumi.logstage.api.IzLogger

final class ContainerExitedCheck[Tag](canBeDestroyed: Boolean) extends ContainerHealthCheck[Tag] {
  override def check(logger: IzLogger, container: DockerContainer[Tag], state: Docker.ContainerState): HealthCheckResult = {
    state match {
      case ContainerState.Running =>
        logger.debug(s"$container still running, marked as unavailable.")
        HealthCheckResult.Unavailable
      case ContainerState.SuccessfullyExited =>
        logger.debug(s"$container successfully exited, marked available.")
        HealthCheckResult.Available
      case ContainerState.NotFound if canBeDestroyed =>
        logger.debug(s"$container was destroyed, marked available.")
        HealthCheckResult.Available
      case ContainerState.NotFound =>
        HealthCheckResult.Terminated("Container not found.")
      case ContainerState.Failed(status) =>
        HealthCheckResult.Terminated(s"Container terminated with non zero code. Code=$status")
    }
  }
}
