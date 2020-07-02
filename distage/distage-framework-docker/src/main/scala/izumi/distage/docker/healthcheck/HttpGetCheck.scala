package izumi.distage.docker.healthcheck

import java.net.{HttpURLConnection, URL}

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.logstage.api.IzLogger

final class HttpGetCheck[Tag](
  portStatus: HealthCheckResult.AvailableOnPorts,
  port: DockerPort,
  useHttps: Boolean,
) extends ContainerHealthCheck[Tag] {
  override def check(logger: IzLogger, container: DockerContainer[Tag]): ContainerHealthCheck.HealthCheckResult = {
    portStatus.availablePorts.firstOption(port) match {
      case Some(availablePort) if portStatus.requiredPortsAccessible =>
        val protocol = if (useHttps) "https" else "http"
        val url = new URL(s"$protocol://${availablePort.hostV4}:${availablePort.port}")
        logger.info(s"Checking docker port $port via $url for $container. Will try to establish HTTP connection.")
        try {
          val connection = url.openConnection().asInstanceOf[HttpURLConnection]
          connection.setRequestMethod("GET")
          connection.setConnectTimeout(container.containerConfig.portProbeTimeout.toMillis.toInt)
          val responseCode = connection.getResponseCode
          if (responseCode != -1) {
            logger.info(s"HTTP connection was successfully established with $port.")
            ContainerHealthCheck.HealthCheckResult.Available
          } else {
            logger.info(s"Cannot establish HTTP connection with $port. Wrong protocol.")
            ContainerHealthCheck.HealthCheckResult.Unavailable
          }
        } catch {
          case t: Throwable =>
            logger.warn(s"Cannot establish HTTP connection with $port due to ${t.getMessage -> "failure"}")
            ContainerHealthCheck.HealthCheckResult.Unavailable
        }
      case _ =>
        ContainerHealthCheck.HealthCheckResult.Unavailable
    }
  }
}
