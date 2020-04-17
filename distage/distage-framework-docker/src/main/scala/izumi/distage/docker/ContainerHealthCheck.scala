package izumi.distage.docker

import java.net.SocketTimeoutException

import izumi.distage.docker.Docker.{AvailablePort, DockerPort, HealthCheckResult}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import izumi.logstage.api.IzLogger

trait ContainerHealthCheck[Tag] {
  def check(logger: IzLogger, container: DockerContainer[Tag]): HealthCheckResult
}

object ContainerHealthCheck {
  def dontCheckPorts[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.JustRunning

  /**
    * Warning: this port probe will not check UDP ports
    */
  def checkAllPorts[T]: ContainerHealthCheck[T] = {
    (logger: IzLogger, container: DockerContainer[T]) =>
      val check = new PortCheck(container.containerConfig.portProbeTimeout.toMillis.toInt)
      val availablePorts: Map[DockerPort, (Seq[AvailablePort], Seq[ResourceCheck.Failure])] = container.ports
        .collect {
          case (port: DockerPort.TCP, bindings) =>
            logger.debug(s"Probing $port on $container...")
            val checkResults = bindings
              .flatMap {
                servicePort =>
                  val candidateHosts = if (servicePort.listenOnV4 != "0.0.0.0") {
                    Seq(servicePort.listenOnV4) ++ servicePort.containerAddressesV4
                  } else {
                    Seq("127.0.0.1") ++ servicePort.containerAddressesV4
                  }

                  candidateHosts.map {
                    host =>
                      (AvailablePort(host, servicePort.port), check.checkPort(host, servicePort.port, s"open port $port on ${container.id}"))
                  }
              }
            val availablePorts = checkResults.collect { case (p, _: ResourceCheck.Success) => p }
            val broken = checkResults.collect { case (p, f: ResourceCheck.Failure) => f }


            (port: DockerPort, (availablePorts, broken))
        }

      val available = availablePorts.mapValues(_._1)
      val broken = availablePorts.mapValues(_._2)

      if (broken.isEmpty) {
        if (available.nonEmpty) {
          HealthCheckResult.WithPorts(available)
        } else {
          HealthCheckResult.Unknown
        }
      } else {
        if (broken.values.flatten.collect { case r: ResourceCheck.ResourceUnavailable if r.cause.exists(_.isInstanceOf[SocketTimeoutException]) => r }.nonEmpty) {
          HealthCheckResult.SocketTimeout
        } else {
          HealthCheckResult.Unknown
        }
      }

  }
}
