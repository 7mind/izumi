package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult.AvailableOnPorts
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult

  final def ++(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = this combine next
  final def combine(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = combineOn(next)
  final def combineOn[T: ClassTag](next: T => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag]) =>
      check(logger, container) match {
        case thisCheck: T =>
          next(thisCheck).check(logger, container) match {
            case HealthCheckResult.Available => thisCheck
            case other => other
          }
        case other => other
      }
  }
}

object ContainerHealthCheck {
  sealed trait HealthCheckResult
  object HealthCheckResult {
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

  def ignore[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Available
  def portCheck[T]: ContainerHealthCheck[T] = new TCPContainerHealthCheck[T]
  def postgreSqlProtocolCheck[T]: ContainerHealthCheck[T] = portCheck.combineOn[AvailableOnPorts](new PostgreSqlProtocolCheck(_))
  def httpGetCheck[T](port: DockerPort): ContainerHealthCheck[T] = portCheck.combineOn[AvailableOnPorts](new HttpGetCheck(_, port))
}
