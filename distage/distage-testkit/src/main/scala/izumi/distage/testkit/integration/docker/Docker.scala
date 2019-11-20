package izumi.distage.testkit.integration.docker

import java.util.concurrent.TimeUnit

import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}

import scala.concurrent.duration.FiniteDuration

object Docker {
  final case class ServicePort(hostsV4: Seq[String], listenOnV4: String, number: Int)
  object ServicePort {
    def local(port: Int): ServicePort = ServicePort(Seq("127.0.0.1"), "127.0.0.1", port)
  }

  final case class ContainerId(name: String) extends AnyVal

  trait DockerPort {
    def number: Int
    def protocol: String
  }

  object DockerPort {
    final case class TCP(number: Int) extends DockerPort {
      override def protocol: String = "tcp"
    }
    final case class UDP(number: Int) extends DockerPort {
      override def protocol: String = "udp"
    }
  }

  final case class RemoteDockerConfig(host: String, tlsVerify: Boolean, certPath: String, config: String)

  final case class DockerRegistryConfig(url: String, username: String, password: String, email: String)

  final case class ClientConfig(
                                 readTimeoutMs: Int,
                                 connectTimeoutMs: Int,
                                 allowReuse: Boolean,
                                 useRemote: Boolean,
                                 useRegistry: Boolean,
                                 remote: Option[RemoteDockerConfig],
                                 registry: Option[DockerRegistryConfig],
                               )

  sealed trait HealthCheckResult
  object HealthCheckResult {
    case object Running extends HealthCheckResult
    case object Uknnown extends HealthCheckResult
    final case class Failed(t: Throwable) extends HealthCheckResult
  }

  trait ContainerHealthCheck[Tag] {
    def check(container: DockerContainer[Tag]): HealthCheckResult
  }
  object ContainerHealthCheck {
    def dontCheckPorts[T]: ContainerHealthCheck[T] = _ => HealthCheckResult.Running

    def checkFirstPort[T](timeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)): ContainerHealthCheck[T] = {
      container: DockerContainer[T] =>
        container.containerConfig.ports.headOption match {
          case Some(value) =>
            checkPort[T](value, timeout).check(container)
          case None =>
            HealthCheckResult.Running
        }
    }

    def checkPort[T](exposedPort: DockerPort, timeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)): ContainerHealthCheck[T] = {
      container: DockerContainer[T] =>
        container.mapping.get(exposedPort) match {
          case Some(value) =>

            val allChecks = value.hostsV4.map {
              host =>
                new PortCheck(timeout.toMillis.intValue()).checkPort(host, value.number, s"open port ${exposedPort} on ${container.id}") match {
                  case _: ResourceCheck.Success =>
                    HealthCheckResult.Running
                  case _: ResourceCheck.Failure =>
                    HealthCheckResult.Uknnown
                }
            }

            if (allChecks.contains(HealthCheckResult.Running)) {
              HealthCheckResult.Running
            } else {
              HealthCheckResult.Uknnown
            }

          case None =>
            HealthCheckResult.Failed(new RuntimeException(s"Port ${exposedPort} is not mapped!"))
        }
    }
  }

  final case class Mount(host: String, container: String)

  final case class ContainerConfig[T](
                                       image: String,
                                       ports: Seq[DockerPort],
                                       env: Map[String, String] = Map.empty,
                                       cmd: Seq[String] = Seq.empty,
                                       entrypoint: Seq[String] = Seq.empty,
                                       cwd: Option[String] = None,
                                       user: Option[String] = None,
                                       mounts: Seq[Mount] = Seq.empty,
                                       reuse: Boolean = true,
                                       healthCheckInterval: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS),
                                       pullTimeout: FiniteDuration = FiniteDuration(120, TimeUnit.SECONDS),
                                       healthCheck: ContainerHealthCheck[T] = ContainerHealthCheck.checkFirstPort[T](),
                                     )

}
