package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.healthcheck.chain.{ChainedContainerHealthCheck, HttpProtocolCheck, PostgreSqlProtocolCheck}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult

  final def combine(next: ChainedContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = this ++ next
  final def ++(next: ChainedContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag]) =>
      check(logger, container) match {
        case status: HealthCheckResult.AvailableOnPorts if status.requiredPortsAccessible =>
          next.check(logger, container, status) match {
            case HealthCheckResult.Ignored | HealthCheckResult.Available => status
            case other => other
          }
        case other => other
      }
  }
}

object ContainerHealthCheck {
  sealed trait HealthCheckResult
  object HealthCheckResult {
    case object Ignored extends HealthCheckResult
    case object Available extends HealthCheckResult
    case object Unavailable extends HealthCheckResult
    final case class AvailableOnPorts(
      availablePorts: VerifiedContainerConnectivity,
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
      requiredPortsAccessible: Boolean,
    ) extends HealthCheckResult
  }

  final case class UnavailablePorts(unavailablePorts: Map[DockerPort, List[(AvailablePort, Option[Throwable])]])

  final case class VerifiedContainerConnectivity(availablePorts: Map[DockerPort, NonEmptyList[AvailablePort]]) {
    override def toString: String = s"accessible=$availablePorts"
  }
  object VerifiedContainerConnectivity {
    def empty: VerifiedContainerConnectivity = VerifiedContainerConnectivity(Map.empty)
  }

  def ignore[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Ignored
  def portCheck[T]: ContainerHealthCheck[T] = new TCPContainerHealthCheck[T]
  def postgreSqlProtocolCheck[T]: ContainerHealthCheck[T] = portCheck ++ new PostgreSqlProtocolCheck[T]
  def httpProtocolCheck[T](port: DockerPort): ContainerHealthCheck[T] = portCheck ++ new HttpProtocolCheck[T](port)
}
