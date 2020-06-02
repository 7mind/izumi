package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker.{AvailablePort, _}
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{HealthCheckResult, VerifiedContainerConnectivity}
import izumi.distage.docker.healthcheck.ContainerHealthCheckBase.PortCandidate
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.logstage.api.IzLogger


abstract class ContainerHealthCheckBase[Tag] extends ContainerHealthCheck[Tag] {
  override final def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult = {

    val tcpPorts: Map[DockerPort.TCP, NonEmptyList[ServicePort]] =
      container.connectivity.dockerPorts
        .collect {
          case (port: DockerPort.TCP, bindings) =>
            (port, bindings)
        }

    val udpPorts: Map[DockerPort.UDP, NonEmptyList[ServicePort]] =
      container.connectivity.dockerPorts
        .collect {
          case (port: DockerPort.UDP, bindings) =>
            (port, bindings)
        }

    perform(logger, container, tcpPorts, udpPorts)

  }

  protected def perform(logger: IzLogger, container: DockerContainer[Tag], tcpPorts: Map[DockerPort.TCP, NonEmptyList[ServicePort]], udpPorts: Map[DockerPort.UDP, NonEmptyList[ServicePort]]): HealthCheckResult.AvailableOnPorts

  protected def findContainerInternalCandidates[T <: DockerPort](container: DockerContainer[Tag], ports: Map[T, NonEmptyList[ServicePort]]): Seq[PortCandidate[T]] = {
    ports.toSeq.flatMap {
      case (mappedPort, _) =>
        container.connectivity.containerAddressesV4.map {
          internalContainerAddress =>
            PortCandidate(mappedPort, AvailablePort(internalContainerAddress, mappedPort.number))
        }
    }
  }

  protected def findDockerHostCandidates[T <: DockerPort](container: DockerContainer[Tag], ports: Map[T, NonEmptyList[ServicePort]]): Seq[PortCandidate[T]] = {
    ports.toSeq.flatMap {
      case (mappedPort, internalBindings) =>
        internalBindings
          .toList
          .flatMap {
            ib =>
              List(
                PortCandidate(mappedPort, AvailablePort(container.connectivity.dockerHost.getOrElse("127.0.0.1"), ib.port)),
                PortCandidate(mappedPort, AvailablePort(ib.listenOnV4, ib.port)),
              )
          }
    }
  }

  protected def tcpPortsGood[T](container: DockerContainer[T], good: VerifiedContainerConnectivity): Boolean = {
    val tcpPorts = container.containerConfig.ports.collect { case t: DockerPort.TCP => t: DockerPort }.toSet
    tcpPorts.diff(good.availablePorts.keySet).isEmpty
  }

}

object ContainerHealthCheckBase {
  case class GoodPort(port: DockerPort, maybeAvailable: AvailablePort)
  case class PortCandidate[+T <: DockerPort](port: T, maybeAvailable: AvailablePort)
  case class FailedPort(port: DockerPort, candidate: PortCandidate[DockerPort], issue: Option[Throwable])
}