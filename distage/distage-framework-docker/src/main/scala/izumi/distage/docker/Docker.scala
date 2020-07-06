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
    def protocol: String
    def toEnvVariable: String
    def portLabel(parts: String*): String
  }
  object DockerPort {
    sealed trait TCPBase extends DockerPort {
      override def protocol: String = "tcp"
    }
    sealed trait UDPBase extends DockerPort {
      override def protocol: String = "tcp"
    }
    sealed trait Static extends DockerPort {
      def number: Int
      override def toString: String = s"$protocol:$number"
      final def toEnvVariable = s"DISTAGE_PORT_${protocol.toUpperCase}_$number"
      final def portLabel(parts: String*): String = (s"distage.port.$protocol.$number" :: parts.toList).mkString(".")
    }
    sealed trait Dynamic extends DockerPort {
      def name: String
      override def toString: String = s"$protocol:$name"
      final def toEnvVariable = s"DISTAGE_PORT_${protocol.toUpperCase}_${name.toUpperCase}"
      final def portLabel(parts: String*): String = (s"distage.port.$protocol.$name" :: parts.toList).mkString(".")
    }
    final case class DynamicTCP(name: String) extends Dynamic with TCPBase
    final case class DynamicUDP(name: String) extends Dynamic with UDPBase
    final case class TCP(number: Int) extends Static with TCPBase
    final case class UDP(number: Int) extends Static with UDPBase
  }

  /**
    * Parameters that define the behavior of this docker container,
    * Will be interpreted by [[ContainerResource]]
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
    * @param cmd      Entrypoint command to use
    *
    * @param entrypoint Docker entrypoint to use
    *
    * @param cwd      Working directory to use inside the docker container
    *
    * @param mounts   Host paths mounted to Volumes inside the docker container
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
    healthCheck: ContainerHealthCheck[T] = ContainerHealthCheck.portCheck[T],
    portProbeTimeout: FiniteDuration = FiniteDuration(200, TimeUnit.MILLISECONDS),
  ) {
    def tcpPorts: Set[DockerPort] = ports.collect { case t: DockerPort.TCPBase => t: DockerPort }.toSet
    def udpPorts: Set[DockerPort] = ports.collect { case t: DockerPort.UDPBase => t: DockerPort }.toSet
  }

  /**
    * Client configuration that will be read from HOCON config.
    * See `docker-reference.conf` for an example configuration.
    * You can `include` the reference configuration if you want to use defaults.
    *
    * @param allowReuse   If true and container's [[ContainerConfig#reuse]] is also true, keeps container alive after
    *                     initialization. If false, the container will be shut down.

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
    readTimeoutMs: Int = 60000,
    connectTimeoutMs: Int = 1000,
    allowReuse: Boolean = true,
    useRemote: Boolean = false,
    useRegistry: Boolean = false,
    remote: Option[RemoteDockerConfig] = None,
    registry: Option[DockerRegistryConfig] = None,
  )
  object ClientConfig {
    implicit val distageConfigReader: DIConfigReader[ClientConfig] = DIConfigReader.derived
  }

  final case class RemoteDockerConfig(host: String, tlsVerify: Boolean, certPath: String, config: String)

  final case class DockerRegistryConfig(url: String, username: String, password: String, email: String)

  final case class Mount(host: String, container: String, noCopy: Boolean = false)

  final case class UnmappedPorts(ports: Seq[DockerPort])

  final case class ContainerConnectivity(
    dockerHost: Option[String],
    containerAddressesV4: Seq[String],
    dockerPorts: Map[DockerPort, NonEmptyList[ServicePort]],
  ) {
    override def toString: String = s"{host: $dockerHost; addresses=$containerAddressesV4; ports=$dockerPorts}"
  }

}
