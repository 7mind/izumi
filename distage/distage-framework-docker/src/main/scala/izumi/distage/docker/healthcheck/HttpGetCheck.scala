package izumi.distage.docker.healthcheck

import java.net.{HttpURLConnection, InetAddress, URL}
import izumi.distage.docker.Docker.{ContainerState, DockerPort}
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.logstage.api.IzLogger

final class HttpGetCheck[Tag](
  portStatus: HealthCheckResult.GoodOnPorts,
  port: DockerPort,
  useHttps: Boolean,
) extends ContainerHealthCheck[Tag] {
  override def check(logger: IzLogger, container: DockerContainer[Tag], state: ContainerState): HealthCheckResult = {
    ContainerHealthCheck.checkIfRunning(state) {
      portStatus.availablePorts.firstOption(port) match {
        case Some(availablePort) if portStatus.allTCPPortsAccessible =>
          val protocol = if (useHttps) "https" else "http"
          val url = new URL(s"$protocol://${availablePort.host.host}:${availablePort.port}")
          logger.info(s"Checking docker port $port via $url for $container. Will try to establish HTTP connection.")
          try {
            val connection = url.openConnection().asInstanceOf[HttpURLConnection]
            connection.setRequestMethod("GET")
            connection.setConnectTimeout(container.containerConfig.portProbeTimeout.toMillis.toInt)
            val responseCode = connection.getResponseCode
            if (responseCode != -1) {
              logger.info(s"HTTP connection was successfully established with $port.")
              HealthCheckResult.Good
            } else {
              logger.info(s"Cannot establish HTTP connection with $port. Wrong protocol.")
              HealthCheckResult.Bad
            }
          } catch {
            case failure: Throwable =>
              logger.warn(s"Cannot establish HTTP connection with $port due to $failure")
              HealthCheckResult.Bad
          }
        case _ =>
          HealthCheckResult.Bad
      }
    }
  }
}
