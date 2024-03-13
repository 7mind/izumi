package izumi.distage.docker.healthcheck

import izumi.distage.docker.model.Docker.{ContainerState, DockerPort}
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.logstage.api.IzLogger

import java.net.{HttpURLConnection, URL}

final class HttpGetCheck(
  portStatus: HealthCheckResult.AvailableOnPorts,
  port: DockerPort,
  useHttps: Boolean,
) extends ContainerHealthCheck {
  override def check(logger: IzLogger, container: DockerContainer[?], state: ContainerState): HealthCheckResult = {
    HealthCheckResult.onRunning(state) {
      portStatus.availablePorts.firstOption(port) match {
        case Some(availablePort) if portStatus.allTCPPortsAccessible =>
          val protocol = if (useHttps) "https" else "http"
          val url = new URL(s"$protocol://${availablePort.hostString}:${availablePort.port}")
          logger.info(s"Checking docker port $port via $url for $container. Will try to establish HTTP connection.")
          try {
            val connection = url.openConnection().asInstanceOf[HttpURLConnection]
            connection.setRequestMethod("GET")
            connection.setConnectTimeout(container.containerConfig.portProbeTimeout.toMillis.toInt)
            val responseCode = connection.getResponseCode
            if (responseCode != -1) {
              logger.info(s"HTTP connection was successfully established with $port.")
              HealthCheckResult.Passed
            } else {
              logger.info(s"Cannot establish HTTP connection with $port. Wrong protocol.")
              HealthCheckResult.Failed(s"Cannot establish HTTP connection with port=$port. Wrong protocol.")
            }
          } catch {
            case failure: Throwable =>
              logger.warn(s"Cannot establish HTTP connection with $port due to $failure")
              HealthCheckResult.Failed(s"Cannot establish HTTP connection with port=$port due to failure=$failure")
          }
        case _ =>
          HealthCheckResult.Failed()
      }
    }
  }
}
