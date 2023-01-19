package izumi.distage.docker

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.*
import izumi.distage.docker.ContainerResource.PortDecl
import izumi.distage.docker.Docker.*
import izumi.distage.docker.DockerClientWrapper.{ContainerDestroyMeta, RemovalReason}
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult.GoodHealthcheck
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{HealthCheckResult, VerifiedContainerConnectivity}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.effect.{QuasiAsync, QuasiIO}
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.functional.Value
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.network.IzSockets
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.annotation.nowarn
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

open class ContainerResource[F[_], Tag](
  val config: Docker.ContainerConfig[Tag],
  val client: DockerClientWrapper[F],
  val logger: IzLogger,
)(implicit
  val F: QuasiIO[F],
  val P: QuasiAsync[F],
) extends Lifecycle.Basic[F, DockerContainer[Tag]] {

  import client.rawClient

  protected[this] val stableLabels: Map[String, String] = {
    val reuseLabel = Map(
      DockerConst.Labels.reuseLabel -> Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse).toString
    )
    reuseLabel ++ client.labels ++ config.userTags
  }

  protected[this] def toExposedPort(port: DockerPort, number: Int): ExposedPort = {
    port match {
      case _: DockerPort.TCPBase => ExposedPort.tcp(number)
      case _: DockerPort.UDPBase => ExposedPort.udp(number)
    }
  }

  def copy(
    config: Docker.ContainerConfig[Tag] = config,
    client: DockerClientWrapper[F] = client,
    logger: IzLogger = logger,
  )(implicit F: QuasiIO[F] = F,
    P: QuasiAsync[F] = P,
  ): ContainerResource[F, Tag] = {
    new ContainerResource[F, Tag](config, client, logger)(F, P)
  }

  override def acquire: F[DockerContainer[Tag]] = F.suspendF {
    val ports = config.ports.map {
      containerPort =>
        val local = IzSockets.temporaryLocalPort()
        val exposedPort = containerPort match {
          case containerPort: DockerPort.Static => toExposedPort(containerPort, containerPort.number)
          case containerPort: DockerPort.Dynamic => toExposedPort(containerPort, local)
        }
        val binding = new PortBinding(Ports.Binding.bindPort(local), exposedPort)
        val labels = Map(
          containerPort.portLabel("defined") -> true.toString,
          containerPort.portLabel() -> local.toString,
        )
        PortDecl(containerPort, local, binding, labels)
    }

    if (Docker.shouldReuse(config.reuse, client.clientConfig.globalReuse)) {
      runReused(ports)
    } else {
      runNew(ports)
    }
  }

  override def release(container: DockerContainer[Tag]): F[Unit] = {
    if (Docker.shouldKillPromptly(container.containerConfig.reuse, container.clientConfig.globalReuse)) {
      client.removeContainer(container.id, ContainerDestroyMeta.ParameterizedContainer(container), RemovalReason.NotReused)
    } else {
      F.maybeSuspend(
        logger.info(s"Will not destroy: $container (${container.containerConfig.reuse}, ${container.clientConfig.globalReuse})")
      )
    }
  }

  protected[this] def await(container0: DockerContainer[Tag]): F[DockerContainer[Tag]] = F.tailRecM((container0, 0)) {
    case (container, attempt) =>
      F.maybeSuspend {
        logger.debug(s"Awaiting until alive: $container...")
        inspectContainerAndGetState(container.id.name).map {
          config.healthCheck.check(logger, container, _)
        }
      }.flatMap {
          case Right(HealthCheckResult.Passed) =>
            F.maybeSuspend {
              logger.info(s"Continuing without port checks: $container")
              Right(container)
            }

          case Right(status: HealthCheckResult.AvailableOnPorts) if status.allTCPPortsAccessible =>
            F.maybeSuspend {
              val out = container.copy(availablePorts = VerifiedContainerConnectivity.HasAvailablePorts(status.availablePorts))
              logger.info(s"Looks good: ${out -> "container"}")
              Right(out)
            }

          case Right(HealthCheckResult.Terminated(failure)) =>
            F.fail(new RuntimeException(s"$container terminated with failure: $failure"))

          case Right(last) =>
            val maxAttempts = config.healthCheckMaxAttempts
            val next = attempt + 1
            if (maxAttempts >= next) {
              logger.debug(s"Health check uncertain, retrying $next/$maxAttempts on $container...")
              P.sleep(config.healthCheckInterval).map(_ => Left((container, next)))
            } else {
              last match {
                case HealthCheckResult.Failed(failure) =>
                  F.fail(new TimeoutException(s"Health checks failed after $maxAttempts attempts for $container: $failure"))

                case HealthCheckResult.UnavailableWithMeta(unavailablePorts, unverifiedPorts) =>
                  val sb = new StringBuilder()
                  sb.append(s"Health checks failed after $maxAttempts attempts: $container\n")
                  if (unverifiedPorts.nonEmpty) {
                    sb.append(s"Unchecked ports:\n")
                    sb.append(unverifiedPorts.niceList())
                  }
                  if (unavailablePorts.unavailablePorts.nonEmpty) {
                    val portErrors = unavailablePorts.unavailablePorts
                      .flatMap {
                        case (port, attempts) =>
                          attempts.map {
                            case (tested, cause) =>
                              (port, tested, cause)
                          }
                      }.map {
                        case (port, tested, None) =>
                          s"- $port with candidate $tested: no diagnostics"
                        case (port, tested, Some(cause)) =>
                          s"- $port with candidate $tested:\n${cause.stackTrace.shift(4)}"
                      }

                    sb.append(s"Unchecked ports:\n")
                    sb.append(portErrors.niceList())
                  }
                  F.fail(new TimeoutException(sb.toString()))

                case HealthCheckResult.Terminated(failure) =>
                  F.fail(new RuntimeException(s"Unexpected condition: $container terminated with failure: $failure"))

                case impossible: GoodHealthcheck =>
                  F.fail(new TimeoutException(s"BUG: good healthcheck $impossible while health checks failed after $maxAttempts attempts: $container"))
              }
            }

          case Left(t) =>
            F.fail(new RuntimeException(s"$container failed due to exception: ${t.stackTrace}", t))
        }
  }

  protected[this] def runReused(ports: Seq[PortDecl]): F[DockerContainer[Tag]] = {
    val retryWait = 200.millis
    val maxAttempts = (config.pullTimeout / retryWait).toInt
    logger.info(s"About to start or find container ${config.image}, ${config.pullTimeout -> "timeout"} ${maxAttempts -> "max lock retries"}...")

    FileLockMutex.withLocalMutex(logger)(
      s"distage-container-resource-${config.image}:${config.ports.mkString(";")}".replaceAll("[:/]", "_"),
      retryWait = retryWait,
      maxAttempts = maxAttempts,
    ) {
      F.suspendF {
        val containers = {
          /*
           * We will filter out containers by "running" status if container exposes any ports to be mapped
           * and by "exited" status also if there are no exposed ports.
           * We don't need to consider "exited" containers with exposed ports, because they will always fail port check.
           *
           * Filtering containers like this allows us to reuse "oneshot" containers that were started and exited eventually.
           * When reusing containers we usually assume that a container runs as a daemon, but if a container exited successfully
           * we can't understand whether that container's run belonged to one of the previous test runs, or to the current one.
           * So containers that exit will be reused only in the scope of the current test run.
           */
          val exitedOpt = if (ports.isEmpty) List(DockerConst.State.exited) else Nil
          val statusFilter = DockerConst.State.running :: exitedOpt
          // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
          try {
            rawClient
              .listContainersCmd()
              .withAncestorFilter(List(config.image).asJava)
              .withStatusFilter(statusFilter.asJava)
              .withLabelFilter(config.userTags.asJava)
              .exec().asScala.toList
              .sortBy(_.getId)
          } catch {
            case c: Throwable =>
              throw new IntegrationCheckException(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c)))
          }
        }
        val candidate = {
          val portSet = ports.map(_.port).toSet
          val candidates = containers.flatMap {
            c =>
              val id = c.getId
              val cInspection = rawClient.inspectContainerCmd(id).exec()
              val cNetworks = cInspection.getNetworkSettings.getNetworks.asScala.keys.toList
              val missingNetworks = config.networks.filterNot(n => cNetworks.contains(n.name))
              val name = cInspection.getName
              mapContainerPorts(cInspection) match {
                case Left(value) =>
                  logger.info(s"Container $name:$id is missing required ports $value so will not be reused")
                  Seq.empty
                case _ if missingNetworks.nonEmpty =>
                  logger.info(s"Container $name:$id is missing required networks $missingNetworks so will not be reused")
                  Seq.empty
                case Right(value) =>
                  Seq((c, cInspection, value))
              }
          }
          if (portSet.nonEmpty) {
            // here we are checking if all ports was successfully mapped
            candidates.find {
              case (_, _, eports) => portSet.diff(eports.dockerPorts.keySet).isEmpty
            }
          } else {
            // or if container has no ports we will check that there is exists container that belongs at least to this test run (or exists container that actually still runs)
            candidates.find(_._2.getState.getRunning).orElse {
              candidates.find {
                case (_, inspection, _) =>
                  val hasLabelsFromSameJvm = (stableLabels.toSet -- client.labelsUnique -- inspection.getConfig.getLabels.asScala.toSet).isEmpty
                  val hasGoodExitCode = inspection.getState.getExitCodeLong == 0L
                  hasLabelsFromSameJvm && hasGoodExitCode
              }
            }
          }
        }

        candidate match {
          case Some((c, cInspection, existingPorts)) =>
            val unverified = DockerContainer[Tag](
              id = ContainerId(c.getId),
              name = cInspection.getName,
              connectivity = existingPorts,
              containerConfig = config,
              clientConfig = client.clientConfig,
              availablePorts = VerifiedContainerConnectivity.NoAvailablePorts(),
              hostName = cInspection.getConfig.getHostName,
              labels = cInspection.getConfig.getLabels.asScala.toMap,
            )
            logger.info(s"Matching container found: ${config.image}->${unverified.name}:${unverified.id}, will try to reuse...")
            await(unverified)

          case None =>
            logger.info(s"No existing container found for ${config.image}, will run new...")
            runNew(ports)
        }
      }
    }
  }

  protected[this] def runNew(ports: Seq[PortDecl]): F[DockerContainer[Tag]] = {
    val allPortLabels = ports.flatMap(p => p.labels).toMap ++ stableLabels

    // use container registry or global registry if `useRegistry` is true
    val imageRegistry = config.registry.orElse(client.globalRegistry)
    val registryAuth = imageRegistry.flatMap(client.getRegistryAuth)
    // render image name with specified registry like registry/repo/image
    val imageName = renderImageName(imageRegistry)

    val baseCmd = rawClient.createContainerCmd(imageName).withLabels(allPortLabels.asJava)

    val volumes = config.mounts.map {
      case Docker.Mount(h, c, true) => new Bind(h, new Volume(c), true)
      case Docker.Mount(h, c, _) => new Bind(h, new Volume(c))
    }

    val portsEnv = ports.map {
      port => port.port.toEnvVariable -> port.binding.getBinding.getHostPortSpec
    }
    val adjustedEnv = portsEnv ++ config.env

    for {
      _ <- F.when(config.autoPull) {
        doPull(imageName, imageRegistry, registryAuth)
      }
      out <- F.maybeSuspend {
        @nowarn("msg=method.*Bind.*deprecated")
        val createContainerCmd = Value(baseCmd)
          .mut(config.name)(_.withName(_))
          .mut(ports.nonEmpty)(_.withExposedPorts(ports.map(_.binding.getExposedPort).asJava))
          .mut(ports.nonEmpty)(_.withPortBindings(ports.map(_.binding).asJava))
          .mut(adjustedEnv.nonEmpty)(_.withEnv(adjustedEnv.map { case (k, v) => s"$k=$v" }.toList.asJava))
          .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
          .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.entrypoint.toList.asJava))
          .mut(config.cwd)(_.withWorkingDir(_))
          .mut(config.user)(_.withUser(_))
          .mut(registryAuth)(_.withAuthConfig(_))
          .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
          .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
          .map(c => c.withHostConfig(c.getHostConfig.withAutoRemove(config.autoRemove)))
          .get

        logger.debug(s"Going to create container from image `${config.image}`...")
        val res = createContainerCmd.exec()

        logger.debug(s"Going to start container ${res.getId -> "id"}...")
        rawClient.startContainerCmd(res.getId).exec()

        val inspection = rawClient.inspectContainerCmd(res.getId).exec()
        val hostName = inspection.getConfig.getHostName
        val maybeMappedPorts = mapContainerPorts(inspection)

        maybeMappedPorts match {
          case Left(value) =>
            throw new RuntimeException(s"Created container from `${config.image}` with ${res.getId -> "id"}, but ports are missing: $value!")

          case Right(mappedPorts) =>
            val container = DockerContainer[Tag](
              id = ContainerId(res.getId),
              name = inspection.getName,
              hostName = hostName,
              labels = inspection.getConfig.getLabels.asScala.toMap,
              containerConfig = config,
              clientConfig = client.clientConfig,
              connectivity = mappedPorts,
              availablePorts = VerifiedContainerConnectivity.NoAvailablePorts(),
            )
            logger.info(s"Created new $container from ${config.image}... Going to attach container ${res.getId -> "id"} to ${config.networks -> "networks"}")
            config.networks.foreach {
              network =>
                rawClient
                  .connectToNetworkCmd()
                  .withContainerId(container.id.name)
                  .withNetworkId(network.id)
                  .exec()
            }

            container
        }
      }
      result <- await(out)
    } yield result
  }

  protected[this] def doPull(imageName: String, registry: Option[String], registryAuth: Option[AuthConfig]): F[Unit] = {
    F.maybeSuspendEither {
      val existingImages = rawClient
        .listImagesCmd().exec()
        .asScala
        .flatMap(i => Option(i.getRepoTags).fold(List.empty[String])(_.toList))
        .toSet
      // test if image exists
      // docker official images may be pulled with or without `library` user prefix, but it being saved locally without prefix
      if (existingImages.contains(config.image) || existingImages.contains(config.image.replace("library/", ""))) {
        logger.info(s"Skipping pull for `${config.image}`. Image already exists.")
        Right(())
      } else {
        logger.info(s"Going to pull `$imageName`...")
        // try to pull image with timeout. If pulling was timed out - return [IntegrationCheckException] to skip tests.
        Try {
          val pullCmd = Value(rawClient.pullImageCmd(imageName))
            .mut(registry)(_.withRegistry(_))
            .mut(registryAuth)(_.withAuthConfig(_))
            .get
          pullCmd.start().awaitCompletion(config.pullTimeout.toMillis, TimeUnit.MILLISECONDS)
        } match {
          case Success(true) => // pulled successfully
            Right(())
          case Success(_) => // timed out
            Left(new IntegrationCheckException(ResourceCheck.ResourceUnavailable(s"Image `${config.image}` pull timeout exception.", None)))
          case Failure(t) => // failure occurred (e.g. rate limiter failure)
            Left(new IntegrationCheckException(ResourceCheck.ResourceUnavailable(s"Image pulling failed due to: ${t.getMessage}", Some(t))))
        }
      }
    }
  }

  protected[this] def inspectContainerAndGetState(containerId: String): Either[Throwable, ContainerState] = {
    try {
      val status = rawClient.inspectContainerCmd(containerId).exec()
      status.getState match {
        case s if s.getRunning => Right(ContainerState.Running)
        case s => Right(ContainerState.Exited(s.getExitCodeLong))
      }
    } catch {
      case _: NotFoundException => Right(ContainerState.NotFound)
      case t: Throwable => Left(t)
    }
  }

  protected[this] def mapContainerPorts(inspection: InspectContainerResponse): Either[UnmappedPorts, ReportedContainerConnectivity] = {
    val network = inspection.getNetworkSettings
    val labels = inspection.getConfig.getLabels
    val ports = config.ports.map {
      containerPort =>
        // if we have dynamic port then we will try find mapped port number in container labels
        val exposedPort = containerPort match {
          case dynamic: DockerPort.Dynamic =>
            Option(labels.get(containerPort.portLabel())).flatMap(_.asInt()).map(toExposedPort(dynamic, _))
          case static: DockerPort.Static =>
            Some(toExposedPort(static, static.number))
        }
        val mappings = exposedPort.toSeq.flatMap(p => Option(network.getPorts.getBindings.get(p))).flatten.flatMap {
          port =>
            ServiceHost(port.getHostIp).map(ServicePort(_, Integer.parseInt(port.getHostPortSpec)))
        }
        (containerPort, NonEmptyList.from(mappings))
    }
    val unmapped = ports.collect { case (cp, None) => cp }

    if (unmapped.nonEmpty) {
      Left(UnmappedPorts(unmapped))
    } else {
      val dockerHost = client.rawClientConfig.getDockerHost.getHost
      val networkAddresses = network.getNetworks.asScala.values.toList.map(_.getIpAddress).flatMap(ServiceHost(_))
      val mapped = ports.collect { case (cp, Some(lst)) => (cp, lst) }

      Right(
        ReportedContainerConnectivity(
          dockerHost = Option(dockerHost),
          containerAddresses = networkAddresses,
          dockerPorts = mapped.toMap,
        )
      )
    }
  }

  private[this] def renderImageName(registry: Option[String]) = {
    lazy val renderedRegistry = registry.filterNot(_.contains("index.docker.io")).map(_ + "/").getOrElse("")
    config.image.split("/").toList match {
      case repo :: image :: Nil =>
        s"$renderedRegistry$repo/$image"
      case image :: Nil =>
        s"${renderedRegistry}library/$image"
      case registry :: repo :: image :: Nil =>
        throw new IllegalArgumentException(s"Use config.regisry instead of manual registry specification: $registry; $repo/$image")
      case other =>
        throw new IllegalArgumentException(s"Unknown image name: $other")
    }
  }

}

object ContainerResource {
  final case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])
}
