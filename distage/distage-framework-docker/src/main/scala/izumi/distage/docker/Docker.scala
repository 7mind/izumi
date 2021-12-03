package izumi.distage.docker

import izumi.distage.config.codec.{DIConfigReader, PureconfigAutoDerive}
import izumi.distage.docker.ContainerNetworkDef.ContainerNetwork
import izumi.distage.docker.Docker.ClientConfig.parseReusePolicy
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.PortCheck.HostPortPair
import pureconfig.ConfigReader

import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

object Docker {
  final case class AvailablePort(host: ServiceHost, port: Int) extends HostPortPair {
    def hostString: String = host.host

    override def addr: InetAddress = host.address
  }
  object AvailablePort {
    def local(port: Int): AvailablePort = hostPort(ServiceHost.local, port)
    def hostPort(host: ServiceHost, port: Int): AvailablePort = AvailablePort(host, port)
  }

  final case class ServicePort(host: ServiceHost, port: Int)

  sealed trait ServiceHost {
    def address: InetAddress
    def host: String

    override final def toString: String = host
  }
  object ServiceHost {
    final case class IPv4(address: Inet4Address) extends ServiceHost {
      override def host: String = address.getHostAddress
    }
    final case class IPv6(address: Inet6Address) extends ServiceHost {
      override def host: String = s"[${address.getHostAddress}]"
    }

    def apply(hostStr: String): Option[ServiceHost] = Try(InetAddress.getByName(hostStr)) match {
      case Success(address: Inet4Address) => Some(IPv4(address))
      case Success(address: Inet6Address) => Some(IPv6(address))
      case _ => None
    }

    // we will try to find local host address or will return default 127.0.0.1
    def local: ServiceHost = Try(InetAddress.getLocalHost) match {
      case Success(address: Inet4Address) => IPv4(address)
      case Success(address: Inet6Address) => IPv6(address)
      case _ => IPv4(InetAddress.getByName("127.0.0.1").asInstanceOf[Inet4Address])
    }

    def zeroAddresses: Set[ServiceHost] = {
      Set(ServiceHost("0.0.0.0"), ServiceHost("::")).flatten
    }
  }

  final case class ContainerId(name: String) extends AnyVal {
    override def toString: String = name
  }

  sealed trait DockerPort {
    def protocol: String
    def toEnvVariable: String
    def portLabel(parts: String*): String
  }
  object DockerPort {
    sealed trait TCPBase extends DockerPort {
      override def protocol: String = "tcp"
    }
    sealed trait UDPBase extends DockerPort {
      override def protocol: String = "udp"
    }
    sealed trait Static extends DockerPort {
      def number: Int
      override def toString: String = s"$protocol:$number"
      final def toEnvVariable: String = s"${DockerConst.Vars.portPrefix}_${protocol.toUpperCase}_$number"
      final def portLabel(parts: String*): String = (s"${DockerConst.Labels.portPrefix}.$protocol.$number" :: parts.toList).mkString(".")
    }
    sealed trait Dynamic extends DockerPort {
      def name: String
      override def toString: String = s"$protocol:$name"
      final def toEnvVariable: String = s"${DockerConst.Vars.portPrefix}_${protocol.toUpperCase}_${name.toUpperCase}"
      final def portLabel(parts: String*): String = (s"${DockerConst.Labels.portPrefix}.$protocol.$name" :: parts.toList).mkString(".")
    }
    final case class DynamicTCP(name: String) extends Dynamic with TCPBase
    final case class DynamicUDP(name: String) extends Dynamic with UDPBase
    final case class TCP(number: Int) extends Static with TCPBase
    final case class UDP(number: Int) extends Static with UDPBase
  }

  sealed abstract class DockerReusePolicy(val reuseEnabled: Boolean, val killEnforced: Boolean)
  object DockerReusePolicy {
    case object ReuseDisabled extends DockerReusePolicy(false, true)
    case object ReuseEnabled extends DockerReusePolicy(true, false)

    implicit val configReader: ConfigReader[DockerReusePolicy] =
      ConfigReader[String].map(name => parseReusePolicy(name))
  }

  private[docker] def shouldReuse(reusePolicy: DockerReusePolicy, globalReuse: DockerReusePolicy): Boolean = {
    globalReuse.reuseEnabled && reusePolicy.reuseEnabled
  }

  private[docker] def shouldKillPromptly(reusePolicy: DockerReusePolicy, globalReuse: DockerReusePolicy): Boolean = {
    reusePolicy.killEnforced || globalReuse.killEnforced
  }

  /**
    * Parameters that define the behavior of this docker container,
    * Will be interpreted by [[izumi.distage.docker.ContainerResource]]
    *
    * @param image    Docker Image to use
    *
    * @param ports    Ports to map on the docker container
    *
    * @param networks Docker networks to connect this container to
    *
    * @param reuse    If true and [[ClientConfig#globalReuse]] is also true, keeps container alive after tests.
    *                 If false, the container will be shut down.
    *                 default: true
    *
    * @param autoRemove Enable autoremove flag (`--rm`) for spawned docker image, ensures prompt pruning of containers running to completion.
    *                   Note: must be disabled if you want to use [[ContainerHealthCheck.exitCodeCheck]]
    *
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
    *
    * @param autoPull Pull the image if it does not exists before starting the container.
    *                 default: true, should only be disabled if you absolutely must manage the image manually.
    */
  final case class ContainerConfig[+Tag](
    image: String,
    ports: Seq[DockerPort],
    name: Option[String] = None,
    env: Map[String, String] = Map.empty,
    cmd: Seq[String] = Seq.empty,
    entrypoint: Seq[String] = Seq.empty,
    cwd: Option[String] = None,
    user: Option[String] = None,
    mounts: Seq[Mount] = Seq.empty,
    networks: Set[ContainerNetwork[?]] = Set.empty,
    reuse: DockerReusePolicy = DockerReusePolicy.ReuseEnabled,
    autoRemove: Boolean = true,
    healthCheckInterval: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS),
    healthCheckMaxAttempts: Int = 120,
    pullTimeout: FiniteDuration = FiniteDuration(120, TimeUnit.SECONDS),
    healthCheck: ContainerHealthCheck = ContainerHealthCheck.portCheck,
    portProbeTimeout: FiniteDuration = FiniteDuration(200, TimeUnit.MILLISECONDS),
    autoPull: Boolean = true,
  ) {
    def tcpPorts: Set[DockerPort] = ports.collect { case t: DockerPort.TCPBase => t: DockerPort }.toSet
    def udpPorts: Set[DockerPort] = ports.collect { case t: DockerPort.UDPBase => t: DockerPort }.toSet
  }

  /**
    * Client configuration that will be read from HOCON config.
    * See `docker-reference.conf` for an example configuration.
    * You can `include` the reference configuration if you want to use defaults.
    *
    * @param globalReuse  If true and container's [[ContainerConfig#reuse]] is also true, keeps container alive after
    *                     initialization. If false, the container will be shut down.
    *
    * @param remote       Options to connect to a Remote or Custom Docker Daemon (e.g. custom unix socket or pipe),
    *                     will try to connect to using these options only if [[useRemote]] is `true`
    *
    * @param useRemote    Connect to Remote Docker Daemon
    *
    * @param useRegistry  Connect to specified Docker Registry
    *
    * @param registry     Options to connect to custom Docker Registry host,
    *                     will try to connect to specified registry, instead of the default if [[useRegistry]] is `true`
    */
  final case class ClientConfig(
    globalReuse: DockerReusePolicy = ClientConfig.defaultReusePolicy,
    useRemote: Boolean = false,
    useRegistry: Boolean = false,
    remote: Option[RemoteDockerConfig] = None,
    registry: Option[DockerRegistryConfig] = None,
  )

  object ClientConfig {
    implicit val configReader: ConfigReader[ClientConfig] = PureconfigAutoDerive.derived
    implicit val diConfigReader: DIConfigReader[ClientConfig] = DIConfigReader.deriveFromPureconfigConfigReader

    val defaultReusePolicy: DockerReusePolicy = {
      DebugProperties.`izumi.distage.docker.reuse`
        .strValue()
        .fold[DockerReusePolicy](DockerReusePolicy.ReuseEnabled)(parseReusePolicy)
    }

    def parseReusePolicy(name: String): DockerReusePolicy = {
      name match {
        case "ReuseDisabled" | "false" =>
          DockerReusePolicy.ReuseDisabled
        case "ReuseEnabled" | "true" =>
          DockerReusePolicy.ReuseEnabled
        case other =>
          throw new IllegalArgumentException(s"Unexpected config value for reuse policy: $other")
      }
    }
  }

  /**
    * @param host Valid options:
    *             - "tcp://X.X.X.X:2375" for Remote Docker Daemon
    *             - "unix:///var/run/docker.sock" for Unix sockets support
    *             - "npipe:////./pipe/docker_engine" for Windows Npipe support
    */
  final case class RemoteDockerConfig(
    host: String,
    tlsVerify: Boolean = false,
    certPath: String = "/home/user/.docker/certs",
    config: String = "/home/user/.docker",
  )

  final case class DockerRegistryConfig(
    url: String,
    username: String,
    password: String,
    email: String,
  )

  final case class Mount(
    hostPath: String,
    containerPath: String,
    noCopy: Boolean = false,
  )

  final case class UnmappedPorts(
    ports: Seq[DockerPort]
  )

  final case class ReportedContainerConnectivity(
    dockerHost: Option[String],
    containerAddresses: Seq[ServiceHost],
    dockerPorts: Map[DockerPort, NonEmptyList[ServicePort]],
  ) {
    override def toString: String = s"{host: $dockerHost; addresses=$containerAddresses; ports=$dockerPorts}"
  }

  sealed trait ContainerState
  object ContainerState {
    case object Running extends ContainerState
    case object NotFound extends ContainerState
    final case class Exited(status: Long) extends ContainerState
  }
}
