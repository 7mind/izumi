package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap}
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck {
  def check(logger: IzLogger, container: DockerContainer[_], state: ContainerState): HealthCheckResult

  final def ++(next: HealthCheckResult => ContainerHealthCheck): ContainerHealthCheck = {
    this.combine(next)
  }
  final def combine(next: HealthCheckResult => ContainerHealthCheck): ContainerHealthCheck = {
    (logger: IzLogger, container: DockerContainer[_], state: ContainerState) =>
      next(check(logger, container, state)).check(logger, container, state)
  }
  final def combineOnPorts(next: HealthCheckResult.AvailableOnPorts => ContainerHealthCheck): ContainerHealthCheck = {
    (logger: IzLogger, container: DockerContainer[_], state: ContainerState) =>
      check(logger, container, state) match {
        case thisCheckResult: HealthCheckResult.AvailableOnPorts =>
          next(thisCheckResult).check(logger, container, state) match {
            case HealthCheckResult.Passed => thisCheckResult
            case other => other
          }
        case other => other
      }
  }
}

object ContainerHealthCheck {
  def portCheck: ContainerHealthCheck = new TCPContainerHealthCheck

  def httpGetCheck(port: DockerPort): ContainerHealthCheck = {
    portCheck.combineOnPorts(new HttpGetCheck(_, port, useHttps = false))
  }

  def httpsGetCheck(port: DockerPort): ContainerHealthCheck = {
    portCheck.combineOnPorts(new HttpGetCheck(_, port, useHttps = true))
  }

  def postgreSqlProtocolCheck(port: DockerPort, user: String, password: String): ContainerHealthCheck = {
    portCheck.combineOnPorts(new PostgreSqlProtocolCheck(_, port, user, password))
  }

  /**
    * Waits until container runs to completion with expected `exitCode`.
    *
    * @note WARNING: [[ContainerConfig#autoRemove]] MUST be set to `false` for this check to pass
    */
  def exitCodeCheck(exitCode: Int = 0): ContainerHealthCheck = new ExitSuccessCheck(exitCode)

  def succeed: ContainerHealthCheck = (_, _, _) => HealthCheckResult.Passed

  sealed trait HealthCheckResult
  object HealthCheckResult {
    sealed trait BadHealthcheck extends HealthCheckResult

    final case class Failed(failure: String = "No diagnostics available") extends BadHealthcheck

    final case class Terminated(failure: String) extends BadHealthcheck

    final case class UnavailableWithMeta(
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
    ) extends BadHealthcheck

    sealed trait GoodHealthcheck extends HealthCheckResult

    case object Passed extends GoodHealthcheck

    final case class AvailableOnPorts(
      availablePorts: AvailablePorts,
      unavailablePorts: UnavailablePorts,
      unverifiedPorts: Set[DockerPort],
      allTCPPortsAccessible: Boolean,
    ) extends GoodHealthcheck

    def onRunning(state: ContainerState)(performCheck: => HealthCheckResult): HealthCheckResult = {
      state match {
        case ContainerState.Running =>
          performCheck

        case ContainerState.Exited(status) =>
          HealthCheckResult.Terminated(s"Container unexpectedly exited with exit code=$status.")

        case ContainerState.NotFound =>
          HealthCheckResult.Terminated("Container not found, expected to find a running container.")
      }
    }
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

}
