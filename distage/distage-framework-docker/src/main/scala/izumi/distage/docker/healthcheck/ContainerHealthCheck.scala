package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult
}


object ContainerHealthCheck {
  sealed trait HealthCheckResult
  object HealthCheckResult {
    case object Ignored extends HealthCheckResult

    final case class PortStatus(
                                 availablePorts: VerifiedContainerConnectivity,
                                 unavailablePorts: UnavailablePorts,
                                 unverifiedPorts: Set[DockerPort],
                                 requiredPortsAccessible: Boolean,
                               ) extends HealthCheckResult
  }

  case class UnavailablePorts(unavailablePorts: Map[DockerPort, List[(AvailablePort, Option[Throwable])]])

  case class VerifiedContainerConnectivity(availablePorts: Map[DockerPort, NonEmptyList[AvailablePort]]) {
    override def toString: String = s"{accessible ports=$availablePorts}"
  }

  object VerifiedContainerConnectivity {
    def empty: VerifiedContainerConnectivity = VerifiedContainerConnectivity(Map.empty)
  }

  def dontCheckPorts[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Ignored

  def checkTCPOnly[T]: ContainerHealthCheck[T] = new TCPContainerHealthCheck[T]
}
