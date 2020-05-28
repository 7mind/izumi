package izumi.distage.docker

import java.util.concurrent.TimeUnit

import izumi.distage.config.codec.DIConfigReader
import izumi.distage.docker.ContainerNetworkDef.ContainerNetwork
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NonEmptyList

import scala.concurrent.duration.FiniteDuration

object Docker {
  final case class AvailablePort(hostV4: String, port: Int)
  object AvailablePort {
    def local(port: Int): AvailablePort = hostPort("127.0.0.1", port)
    def hostPort(host: String, port: Int): AvailablePort = AvailablePort(host, port)
  }

  final case class ServicePort(listenOnV4: String, port: Int)

  final case class ContainerId(name: String) extends AnyVal

  trait DockerPort {
    def number: Int
    def protocol: String
    override def toString: String = s"$protocol:$number"
  }
  object DockerPort {
    final case class TCP(number: Int) extends DockerPort {
      override def protocol: String = "tcp"
    }
    final case class UDP(number: Int) extends DockerPort {
      override def protocol: String = "udp"
    }
  }

  /**
    * Parameters that define the behavior of this docker container,
    * Will be interpreted by [[DockerContainer.ContainerResource]]
    *
    * @param image    Docker Image to use
    *
    * @param ports    Ports to map on the docker container
    *
    * @param networks Docker networks to connect this container to
    *
    * @param reuse    If true and [[ClientConfig#allowReuse]] is also true, keeps container alive after tests.
    *                 If false, the container will be shut down.
    *                 default: true

    * @param healthCheck The function to use to test if a container has started already,
    *                    by default probes to check if all [[ports]] are open and proceeds if so.
    *
    * @param healthCheckInterval Sleep interval between [[healthCheck]]s
    *                            default: 1 second
    *
    * @param portProbeTimeout Sleep interval between port probes in the default [[healthCheck]]
    *                         default: 200 milliseconds
    *
    * @param pullTimeout Maximum amount of time to wait for `docker pull` to download the image
    *                    default: 120 seconds
    *
    * @param name     Name of the container, if left at `None` Docker will generate a random name
    *
    * @param env      Setup environment variables visible inside docker container
    *
    * @param entrypoint Docker entrypoint to use
    *
    * @param cwd      Working directory to use inside the docker container
    *
    * @param mounts   Host paths mounted to Volumes inside the docker container
    *
    *
    */
  final case class ContainerConfig[T](
                                       image: String,
                                       ports: Seq[DockerPort],
                                       name: Option[String] = None,
                                       env: Map[String, String] = Map.empty,
                                       cmd: Seq[String] = Seq.empty,
                                       entrypoint: Seq[String] = Seq.empty,
                                       cwd: Option[String] = None,
                                       user: Option[String] = None,
                                       mounts: Seq[Mount] = Seq.empty,
                                       networks: Set[ContainerNetwork[_]] = Set.empty,
                                       reuse: Boolean = true,
                                       healthCheckInterval: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS),
                                       healthCheckMaxAttempts: Int = 120,
                                       pullTimeout: FiniteDuration = FiniteDuration(120, TimeUnit.SECONDS),
                                       healthCheck: ContainerHealthCheck[T] = ContainerHealthCheck.checkTCPOnly[T],
                                       portProbeTimeout: FiniteDuration = FiniteDuration(200, TimeUnit.MILLISECONDS)
  )

  /**
    * Client configuration that will be read from HOCON config.
    * See `docker-reference.conf` for an example configuration.
    * You can `include` the reference configuration if you want to use defaults.
    *
    * @param allowReuse   If true and container's [[ContainerConfig#reuse]] is also true, keeps container alive after tests.
    *                     If false, the container will be shut down.

    * @param remote       Options to connect to a Remote Docker Daemon,
    *                     will try to connect to remote docker if [[useRemote]] is `true`
    *
    * @param registry     Options to connect to custom Docker Registry host,
    *                     will try to connect to specified registry, instead of the default if [[useRegistry]] is `true`
    *
    * @param readTimeoutMs    Read timeout in milliseconds
    *
    * @param connectTimeoutMs Connect timeout in milliseconds
    *
    * @param useRemote        Connect to Remote Docker Daemon
    *
    * @param useRegistry      Connect to speicifed Docker Registry
    */
  final case class ClientConfig(
    readTimeoutMs: Int,
    connectTimeoutMs: Int,
    allowReuse: Boolean,
    useRemote: Boolean,
    useRegistry: Boolean,
    remote: Option[RemoteDockerConfig],
    registry: Option[DockerRegistryConfig],
  )
  object ClientConfig {
    implicit val distageConfigReader: DIConfigReader[ClientConfig] = DIConfigReader.derived
  }

  final case class RemoteDockerConfig(host: String, tlsVerify: Boolean, certPath: String, config: String)

  final case class DockerRegistryConfig(url: String, username: String, password: String, email: String)

  final case class Mount(hostPath: String, containerPath: String, noCopy: Boolean = false)

  case class UnmappedPorts(ports: Seq[DockerPort])

  case class ContainerConnectivity(
                                    dockerHost: Option[String],
                                    containerAddressesV4: Seq[String],
                                    dockerPorts: Map[DockerPort, NonEmptyList[ServicePort]],
                                  ) {
    override def toString: String = s"{host: $dockerHost; addresses=$containerAddressesV4; ports=$dockerPorts}"
  }


}
