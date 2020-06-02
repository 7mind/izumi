package izumi.distage.docker.healthcheck.chain

import java.net.{HttpURLConnection, URL}

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.logstage.api.IzLogger

final class HttpProtocolCheck[Tag](
  port: DockerPort
) extends ChainedContainerHealthCheck[Tag] {
  override def check(logger: IzLogger, container: DockerContainer[Tag], portStatus: HealthCheckResult.AvailableOnPorts): ContainerHealthCheck.HealthCheckResult = {
    portStatus.availablePorts.availablePorts.get(port) match {
      case Some(value) if portStatus.requiredPortsAccessible =>
        val availablePort = value.head
        val url = new URL(s"http://${availablePort.hostV4}:${availablePort.port}")
        logger.info(s"Checking docker port $port via $url. Will try to establish HTTP connection.")
        try {
          val connection = url.openConnection().asInstanceOf[HttpURLConnection]
          connection.setRequestMethod("GET")
          connection.setConnectTimeout(container.containerConfig.portProbeTimeout.toMillis.toInt)
          val responseCode = connection.getResponseCode
          if (responseCode != -1) {
            logger.info(s"HTTP connection was successfully established with $port.")
            ContainerHealthCheck.HealthCheckResult.Available
          } else {
            logger.debug(s"Cannot establish HTTP connection with $port. Wrong protocol.")
            ContainerHealthCheck.HealthCheckResult.Unavailable
          }
        } catch {
          case t: Throwable =>
            logger.debug(s"Cannot establish HTTP connection with $port due to ${t.getMessage -> "failure"}")
            ContainerHealthCheck.HealthCheckResult.Unavailable
        }
      case _ => ContainerHealthCheck.HealthCheckResult.Ignored
    }
  }
}
