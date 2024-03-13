package izumi.distage.docker.healthcheck

import izumi.distage.docker.model.Docker.*
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{AvailablePorts, HealthCheckResult}
import izumi.distage.docker.healthcheck.ContainerHealthCheckBase.PortCandidate
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

abstract class ContainerHealthCheckBase extends ContainerHealthCheck {
  protected def perform(
    logger: IzLogger,
    container: DockerContainer[?],
    tcpPorts: Map[DockerPort.TCPBase, NEList[ServicePort]],
    udpPorts: Map[DockerPort.UDPBase, NEList[ServicePort]],
  ): HealthCheckResult

  override final def check(logger: IzLogger, container: DockerContainer[?], state: ContainerState): HealthCheckResult = {
    HealthCheckResult.onRunning(state) {
      val tcpPorts: Map[DockerPort.TCPBase, NEList[ServicePort]] =
        container.connectivity.dockerPorts.collect {
          case (port: DockerPort.TCPBase, bindings) =>
            (port, bindings)
        }

      val udpPorts: Map[DockerPort.UDPBase, NEList[ServicePort]] =
        container.connectivity.dockerPorts.collect {
          case (port: DockerPort.UDP, bindings) =>
            (port, bindings)
        }

      perform(logger, container, tcpPorts, udpPorts)
    }
  }

  protected def findContainerInternalCandidates[P <: DockerPort](container: DockerContainer[?], ports: Map[P, NEList[ServicePort]]): Seq[PortCandidate[P]] = {
    val labels = container.labels
    val addresses = container.connectivity.containerAddresses
    ports.toSeq.flatMap {
      case (mappedPort, _) =>
        // if we have dynamic port then we will try find mapped port number in container labels
        val portNumber = mappedPort match {
          case dynamic: DockerPort.Dynamic => labels.get(dynamic.portLabel()).flatMap(_.asInt())
          case static: DockerPort.Static => Some(static.number)
        }
        portNumber.toSeq.flatMap {
          number =>
            addresses.map {
              internalContainerAddress =>
                PortCandidate(mappedPort, AvailablePort(internalContainerAddress, number))
            }
        }
    }
  }

  protected def findDockerHostCandidates[P <: DockerPort](container: DockerContainer[?], ports: Map[P, NEList[ServicePort]]): Seq[PortCandidate[P]] = {
    ports.toSeq.flatMap {
      case (mappedPort, internalBindings) =>
        internalBindings.toList.flatMap {
          ib =>
            List(
              PortCandidate(mappedPort, AvailablePort(container.connectivity.dockerHost.flatMap(ServiceHost(_)).getOrElse(ServiceHost.local), ib.port)),
              PortCandidate(mappedPort, AvailablePort(ib.host, ib.port)),
            )
        }
    }
  }

  protected def tcpPortsGood(container: DockerContainer[?], good: AvailablePorts): Boolean = {
    val tcpPorts = container.containerConfig.tcpPorts
    tcpPorts.diff(good.availablePorts.toMap.keySet).isEmpty
  }

}

object ContainerHealthCheckBase {
  final case class PortCandidate[+T <: DockerPort](port: T, maybeAvailable: AvailablePort)

  final case class GoodPort(port: DockerPort, maybeAvailable: AvailablePort)
  final case class FailedPort(port: DockerPort, candidate: PortCandidate[DockerPort], issue: Option[Throwable])
}
