package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap}
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag], state: ContainerState): HealthCheckResult

  final def ++(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = this combine next
  final def combine(next: HealthCheckResult => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag], state: ContainerState) =>
      next(check(logger, container, state)).check(logger, container, state)
  }
  final def combineOnPorts(next: HealthCheckResult.GoodOnPorts => ContainerHealthCheck[Tag]): ContainerHealthCheck[Tag] = {
    (logger: IzLogger, container: DockerContainer[Tag], state: ContainerState) =>
      check(logger, container, state) match {
        case thisCheckResult: HealthCheckResult.GoodOnPorts =>
          next(thisCheckResult).check(logger, container, state) match {
            case HealthCheckResult.Good => thisCheckResult
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

  def succeed[T]: ContainerHealthCheck[T] = (_, _, _) => HealthCheckResult.Good

  def exited[T](canBeDestroyed: Boolean): ContainerHealthCheck[T] = {
    new ContainerExitedCheck[T](canBeDestroyed)
  }

  sealed trait HealthCheckResult
  object HealthCheckResult {
    sealed trait BadHealth extends HealthCheckResult
    case object Bad extends BadHealth

    final case class BadWithMeta(
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
    ) extends BadHealth

    sealed trait GoodHealth extends HealthCheckResult
    case object Good extends GoodHealth

    final case class GoodOnPorts(
      availablePorts: AvailablePorts,
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
      allTCPPortsAccessible: Boolean,
    ) extends GoodHealth

    final case class Terminated(failure: String) extends HealthCheckResult
  }

  final case class AvailablePorts(availablePorts: NonEmptyMap[DockerPort, NonEmptyList[AvailablePort]]) {
    def get(port: DockerPort): Option[NonEmptyList[AvailablePort]] = availablePorts.toMap.get(port)

    def firstOption(port: DockerPort): Option[AvailablePort] = availablePorts.toMap.get(port).map(_.head)

    override def toString: String = s"accessible=$availablePorts"
  }

  final case class UnavailablePorts(unavailablePorts: Map[DockerPort, List[(AvailablePort, Option[Throwable])]]) extends AnyVal {
    override def toString: String = s"unavailable=$unavailablePorts"
  }

  sealed trait VerifiedContainerConnectivity {
    def get(port: DockerPort): Option[NonEmptyList[AvailablePort]]
    def firstOption(port: DockerPort): Option[AvailablePort]

    final def first(port: DockerPort): AvailablePort = firstOption(port) match {
      case Some(value) =>
        value
      case None =>
        throw new java.util.NoSuchElementException(s"Port $port is unavailable")
    }

    final def apply(port: DockerPort): NonEmptyList[AvailablePort] = get(port) match {
      case Some(value) =>
        value
      case None =>
        throw new java.util.NoSuchElementException(s"Port $port is unavailable")
    }
  }
  object VerifiedContainerConnectivity {
    case class HasAvailablePorts(availablePorts: AvailablePorts) extends VerifiedContainerConnectivity {
      override def get(port: DockerPort): Option[NonEmptyList[AvailablePort]] = availablePorts.get(port)

      override def firstOption(port: DockerPort): Option[AvailablePort] = availablePorts.firstOption(port)
    }

    case class NoAvailablePorts() extends VerifiedContainerConnectivity {
      override def get(port: DockerPort): Option[NonEmptyList[AvailablePort]] = None

      override def firstOption(port: DockerPort): Option[AvailablePort] = None
    }
  }


  def checkIfRunning(state: ContainerState)(performCheck: => HealthCheckResult): HealthCheckResult = {
    state match {
      case ContainerState.Running =>
        performCheck
      case ContainerState.SuccessfullyExited =>
        HealthCheckResult.Terminated("Container unexpectedly exited with code 0.")
      case ContainerState.NotFound =>
        HealthCheckResult.Terminated("Container not found.")
      case ContainerState.Failed(status) =>
        HealthCheckResult.Terminated(s"Container terminated with non zero code. Code=$status")
    }
  }
}
