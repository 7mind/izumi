package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult

  final def combine(next: ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = this ++ next
  final def ++(next: ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag]) =>
      check(logger, container) match {
        case status: HealthCheckResult.PortStatus if status.requiredPortsAccessible =>
          next.check(logger, container) match {
            case HealthCheckResult.Ignored | HealthCheckResult.ContainerAvailable => status
            case nextStatus: HealthCheckResult.PortStatus => status.merge(nextStatus)
          }
        case HealthCheckResult.ContainerAvailable => next.check(logger, container)
        case other => other
      }
  }
}

object ContainerHealthCheck {
  sealed trait HealthCheckResult
  object HealthCheckResult {
    case object Ignored extends HealthCheckResult
    case object ContainerAvailable extends HealthCheckResult
    final case class PortStatus(
      availablePorts: VerifiedContainerConnectivity,
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
      requiredPortsAccessible: Boolean,
    ) extends HealthCheckResult {
      def merge(other: PortStatus): PortStatus = {
        PortStatus(
          VerifiedContainerConnectivity(availablePorts.availablePorts ++ other.availablePorts.availablePorts),
          UnavailablePorts(unavailablePorts.unavailablePorts ++ other.unavailablePorts.unavailablePorts),
          unverifiedPorts ++ other.unverifiedPorts,
          requiredPortsAccessible && other.requiredPortsAccessible,
        )
      }
    }
  }

  case class UnavailablePorts(unavailablePorts: Map[DockerPort, List[(AvailablePort, Option[Throwable])]])

  case class VerifiedContainerConnectivity(availablePorts: Map[DockerPort, NonEmptyList[AvailablePort]]) {
    override def toString: String = s"accessible=$availablePorts"
  }

  object VerifiedContainerConnectivity {
    def empty: VerifiedContainerConnectivity = VerifiedContainerConnectivity(Map.empty)
  }

  def dontCheckPorts[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Ignored

  def checkTCPOnly[T]: ContainerHealthCheck[T] = new TCPContainerHealthCheck[T]
}
