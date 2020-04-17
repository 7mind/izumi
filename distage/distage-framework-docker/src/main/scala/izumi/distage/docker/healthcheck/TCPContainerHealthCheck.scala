package izumi.distage.docker.healthcheck

import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{HealthCheckResult, UnavailablePorts, VerifiedContainerConnectivity}
import izumi.distage.docker.healthcheck.ContainerHealthCheckBase.{FailedPort, GoodPort, PortCandidate}
import izumi.functional.IzEither._
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger


class TCPContainerHealthCheck[Tag] extends ContainerHealthCheckBase[Tag] {

  override protected def perform(logger: IzLogger, container: DockerContainer[Tag], tcpPorts: Map[DockerPort.TCP, NonEmptyList[ServicePort]], udpPorts: Map[DockerPort.UDP, NonEmptyList[ServicePort]]): HealthCheckResult.PortStatus = {
    val check = new PortCheck(container.containerConfig.portProbeTimeout.toMillis.toInt)

    val dockerHostCandidates = findDockerHostCandidates(container, tcpPorts)
    val containerCandidates = findContainerInternalCandidates(container, tcpPorts)

    val allCandidates = (dockerHostCandidates ++ containerCandidates)
      .distinct
      .filterNot(_.maybeAvailable.hostV4 == "0.0.0.0")

    logger.debug(s"${container.id -> "container"}: going to check ${allCandidates.map { case PortCandidate(k, v) => s"if $k is available at $v" }.niceList() -> "port mappings"}")


    val checks = allCandidates.map {
      case PortCandidate(dp, ap) =>
        check.checkPort(ap.hostV4, ap.port, s"open port ${ap.hostV4}:${ap.port} for ${container.id}") match {
          case ResourceCheck.Success() =>
            Right(GoodPort(dp, ap))
          case ResourceCheck.ResourceUnavailable(_, cause) =>
            Left(FailedPort(dp, PortCandidate(dp, ap), cause))
        }
    }

    val (bad, good) = checks.lrPartition

    val errors = UnavailablePorts(
      bad
        .map { case FailedPort(a, b, c) => (a, (b.maybeAvailable, c)) }
        .toMultimap
        .toSeq
        .map {
          case (dp, ap) =>
            (dp: DockerPort, ap.toList)
        }
        .toMap)

    val available = VerifiedContainerConnectivity(
      good
        .map { case GoodPort(a, b) => (a, b) }
        .toMultimap
        .toSeq
        .map {
          case (dp, ap) =>
            (dp: DockerPort, NonEmptyList(ap.head, ap.tail.toList: _*))
        }
        .toMap
    )

    HealthCheckResult.PortStatus(
      available,
      errors,
      udpPorts.keySet.map(p => p: DockerPort),
      tcpPortsGood(container, available)
    )
  }
}

