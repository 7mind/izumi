package izumi.distage.docker.healthcheck.chain

import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult.AvailableOnPorts
import izumi.logstage.api.IzLogger

trait ChainedContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag], portStatus: AvailableOnPorts): HealthCheckResult
}
