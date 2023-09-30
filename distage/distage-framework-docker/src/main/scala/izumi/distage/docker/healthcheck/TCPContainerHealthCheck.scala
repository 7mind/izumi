package izumi.distage.docker.healthcheck

import izumi.distage.docker.model.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{AvailablePorts, HealthCheckResult, UnavailablePorts}
import izumi.distage.docker.healthcheck.ContainerHealthCheckBase.{FailedPort, GoodPort, PortCandidate}
import izumi.functional.IzEither._
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.{NEList, NEMap}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

class TCPContainerHealthCheck extends ContainerHealthCheckBase {

  override protected def perform(
    logger: IzLogger,
    container: DockerContainer[?],
    tcpPorts: Map[DockerPort.TCPBase, NEList[ServicePort]],
    udpPorts: Map[DockerPort.UDPBase, NEList[ServicePort]],
  ): HealthCheckResult = {
    val check = new PortCheck(container.containerConfig.portProbeTimeout)

    val dockerHostCandidates = findDockerHostCandidates(container, tcpPorts)
    val containerCandidates = findContainerInternalCandidates(container, tcpPorts)

    val allCandidates = (dockerHostCandidates ++ containerCandidates).distinct
      .filterNot(c => ServiceHost.zeroAddresses.contains(c.maybeAvailable.host))

    logger.debug(s"going to check ports on $container: ${allCandidates.map { case PortCandidate(k, v) => s"if $k is available at $v" }.niceList() -> "port mappings"}")

    val checks = allCandidates.map {
      case PortCandidate(dp, ap) =>
        check.checkAddressPort(ap.host.address, ap.port, s"open port ${ap.host}:${ap.port} for ${container.id}") match {
          case ResourceCheck.Success() =>
            Right(GoodPort(dp, ap))
          case ResourceCheck.ResourceUnavailable(_, cause) =>
            Left(FailedPort(dp, PortCandidate(dp, ap), cause))
        }
    }

    val (bad, good) = checks.biPartition

    val errored = UnavailablePorts(
      bad.iterator
        .map { case FailedPort(a, b, c) => (a, (b.maybeAvailable, c)) }
        .toMultimapView.map {
          case (dp, ap) =>
            (dp: DockerPort, ap.toList)
        }.toMap
    )

    val succeded = good.iterator
      .map { case GoodPort(a, b) => (a, b) }
      .toMultimapView.map {
        case (dp, ap) =>
          (dp: DockerPort, NEList(ap.head, ap.tail))
      }.toMap

    NEMap.from(succeded) match {
      case Some(value) =>
        val available = AvailablePorts(value)
        val allTCPPortsAvailable = tcpPortsGood(container, available)
        HealthCheckResult.AvailableOnPorts(
          availablePorts = available,
          unavailablePorts = errored,
          unverifiedPorts = udpPorts.keySet.map(p => p: DockerPort),
          allTCPPortsAccessible = allTCPPortsAvailable,
        )

      case None =>
        if (container.containerConfig.tcpPorts.isEmpty) {
          HealthCheckResult.Passed
        } else {
          HealthCheckResult.UnavailableWithMeta(
            unavailablePorts = errored,
            unverifiedPorts = udpPorts.keySet.map(p => p: DockerPort),
          )
        }
    }

  }
}
