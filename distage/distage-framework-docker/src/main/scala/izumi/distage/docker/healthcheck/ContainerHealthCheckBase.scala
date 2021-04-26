package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker.{AvailablePort, _}
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{AvailablePorts, HealthCheckResult}
import izumi.distage.docker.healthcheck.ContainerHealthCheckBase.PortCandidate
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

abstract class ContainerHealthCheckBase[Tag] extends ContainerHealthCheck[Tag] {
  override final def check(logger: IzLogger, container: DockerContainer[Tag], state: ContainerState): HealthCheckResult = onRunningState(state) {
    val tcpPorts: Map[DockerPort.TCPBase, NonEmptyList[ServicePort]] =
      container.connectivity.dockerPorts
        .collect {
          case (port: DockerPort.TCPBase, bindings) =>
            (port, bindings)
        }

    val udpPorts: Map[DockerPort.UDPBase, NonEmptyList[ServicePort]] =
      container.connectivity.dockerPorts
        .collect {
          case (port: DockerPort.UDP, bindings) =>
            (port, bindings)
        }

    perform(logger, container, tcpPorts, udpPorts)
  }

  protected def perform(
    logger: IzLogger,
    container: DockerContainer[Tag],
    tcpPorts: Map[DockerPort.TCPBase, NonEmptyList[ServicePort]],
    udpPorts: Map[DockerPort.UDPBase, NonEmptyList[ServicePort]],
  ): HealthCheckResult

  protected def findContainerInternalCandidates[T <: DockerPort](container: DockerContainer[Tag], ports: Map[T, NonEmptyList[ServicePort]]): Seq[PortCandidate[T]] = {
    val labels = container.labels
    val addresses = container.connectivity.containerAddressesV4
    ports.toSeq.flatMap {
      case (mappedPort, _) =>
        // if we have dynamic port then we will try find mapped port number in container labels
        val portNumber = (mappedPort: DockerPort) match {
          case dynamic: DockerPort.Dynamic => labels.get(dynamic.portLabel()).flatMap(_.asInt())
          case static: DockerPort.Static => Some(static.number)
        }
        portNumber.toSeq.flatMap {
          number =>
            addresses.map {
              internalContainerAddress =>
                PortCandidate[T](mappedPort, AvailablePort(internalContainerAddress, number))
            }
        }
    }
  }

  protected def findDockerHostCandidates[T <: DockerPort](container: DockerContainer[Tag], ports: Map[T, NonEmptyList[ServicePort]]): Seq[PortCandidate[T]] = {
    ports.toSeq.flatMap {
      case (mappedPort, internalBindings) =>
        internalBindings.toList
          .flatMap {
            ib =>
              List(
                PortCandidate(mappedPort, AvailablePort(container.connectivity.dockerHost.getOrElse("127.0.0.1"), ib.port)),
                PortCandidate(mappedPort, AvailablePort(ib.listenOnV4, ib.port)),
              )
          }
    }
  }

  protected def tcpPortsGood[T](container: DockerContainer[T], good: AvailablePorts): Boolean = {
    val tcpPorts = container.containerConfig.tcpPorts
    tcpPorts.diff(good.availablePorts.toMap.keySet).isEmpty
  }

}

object ContainerHealthCheckBase {
  case class GoodPort(port: DockerPort, maybeAvailable: AvailablePort)
  case class PortCandidate[+T <: DockerPort](port: T, maybeAvailable: AvailablePort)
  case class FailedPort(port: DockerPort, candidate: PortCandidate[DockerPort], issue: Option[Throwable])
}
