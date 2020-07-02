package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap}
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult

  final def ++(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = this combine next
  final def combine(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag]) =>
      next(check(logger, container)).check(logger, container)
  }
  final def combineOnPorts(next: HealthCheckResult.AvailableOnPorts => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag]) =>
      check(logger, container) match {
        case thisCheckResult: HealthCheckResult.AvailableOnPorts =>
          next(thisCheckResult).check(logger, container) match {
            case HealthCheckResult.Available => thisCheckResult
            case other => other
          }
        case other => other
      }
  }
}

object ContainerHealthCheck {
  def portCheck[T]: ContainerHealthCheck[T] = {
    new TCPContainerHealthCheck[T]
  }

  def httpGetCheck[T](port: DockerPort): ContainerHealthCheck[T] = {
    portCheck.combineOnPorts(new HttpGetCheck(_, port, useHttps = false))
  }
  def httpsGetCheck[T](port: DockerPort): ContainerHealthCheck[T] = {
    portCheck.combineOnPorts(new HttpGetCheck(_, port, useHttps = true))
  }

  def postgreSqlProtocolCheck[T](port: DockerPort, user: String, password: String): ContainerHealthCheck[T] = {
    portCheck.combineOnPorts(new PostgreSqlProtocolCheck(_, port, user, password))
  }

  def succeed[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Available

  sealed trait HealthCheckResult
  object HealthCheckResult {
    sealed trait BadHealthcheck extends HealthCheckResult
    case object Unavailable extends BadHealthcheck

    sealed trait GoodHealthcheck extends HealthCheckResult
    case object Available extends GoodHealthcheck

    final case class AvailableOnPorts(
      availablePorts: AvailablePorts,
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
      allTCPPortsAccessible: Boolean,
    ) extends GoodHealthcheck

    final case class UnavailableWithMeta(
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
    ) extends BadHealthcheck
  }

  final case class AvailablePorts(availablePorts: NonEmptyMap[DockerPort, NonEmptyList[AvailablePort]]) {
    def apply(port: DockerPort): NonEmptyList[AvailablePort] = availablePorts(port)

    def first(port: DockerPort): AvailablePort = availablePorts(port).head

    def get(port: DockerPort): Option[NonEmptyList[AvailablePort]] = availablePorts.toMap.get(port)

    def firstOption(port: DockerPort): Option[AvailablePort] = availablePorts.toMap.get(port).map(_.head)

    override def toString: String = s"accessible=$availablePorts"
  }

  final case class UnavailablePorts(unavailablePorts: Map[DockerPort, List[(AvailablePort, Option[Throwable])]]) extends AnyVal {
    override def toString: String = s"unavailable=$unavailablePorts"
  }
}
