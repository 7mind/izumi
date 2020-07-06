package izumi.distage.docker

import java.util.concurrent.{TimeUnit, TimeoutException}

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._

import scala.annotation.nowarn
import izumi.distage.docker.Docker._
import izumi.distage.docker.DockerClientWrapper.ContainerDestroyMeta
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{HealthCheckResult, VerifiedContainerConnectivity}
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult.GoodHealthcheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.functional.Value
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.network.IzSockets
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

case class ContainerResource[F[_], T](
  config: Docker.ContainerConfig[T],
  client: DockerClientWrapper[F],
  logger: IzLogger,
)(implicit
  val F: DIEffect[F],
  val P: DIEffectAsync[F],
) extends DIResource[F, DockerContainer[T]] {

  import ContainerResource._

  private[this] val rawClient = client.rawClient

  private[this] def toExposedPort(port: DockerPort, number: Int): ExposedPort = {
    port match {
      case _: DockerPort.TCPBase => ExposedPort.tcp(number)
      case _: DockerPort.UDPBase => ExposedPort.udp(number)
    }
  }

  private[this] def shouldReuse(config: Docker.ContainerConfig[T]): Boolean = {
    config.reuse && client.clientConfig.allowReuse
  }

  override def acquire: F[DockerContainer[T]] = F.suspendF {
    val ports = config.ports.map {
      containerPort =>
        val local = IzSockets.temporaryLocalPort()
        val exposedPort = containerPort match {
          case containerPort: DockerPort.Static => toExposedPort(containerPort, containerPort.number)
          case containerPort: DockerPort.Dynamic => toExposedPort(containerPort, local)
        }
        val binding = new PortBinding(Ports.Binding.bindPort(local), exposedPort)
        val labels = Map(
          containerPort.portLabel("defined") -> "true",
          containerPort.portLabel() -> local.toString,
        )
        PortDecl(containerPort, local, binding, labels)
    }

    if (shouldReuse(config)) {
      runReused(ports)
    } else {
      doRun(ports)
    }
  }

  override def release(resource: DockerContainer[T]): F[Unit] = {
    if (!shouldReuse(resource.containerConfig)) {
      client.destroyContainer(resource.id, ContainerDestroyMeta.ParameterizedContainer(resource))
    } else {
      F.unit
    }
  }

  def await(container: DockerContainer[T], attempt: Int): F[DockerContainer[T]] = {
    F.maybeSuspend {
      logger.debug(s"Awaiting until alive: $container...")
      try {
        val status = rawClient.inspectContainerCmd(container.id.name).exec()
        // if container is running or does not have any ports
        if (status.getState.getRunning || (config.ports.isEmpty && status.getState.getExitCodeLong == 0L)) {
          logger.debug(s"Trying healthcheck on running $container...")
          Right(config.healthCheck.check(logger, container))
        } else {
          Left(new RuntimeException(s"$container exited, status: ${status.getState}"))
        }
      } catch {
        case t: Throwable =>
          Left(t)
      }
    }.flatMap {
        case Right(HealthCheckResult.Available) =>
          F.maybeSuspend {
            logger.info(s"Continuing without port checks: $container")
            container
          }

        case Right(status: HealthCheckResult.AvailableOnPorts) if status.allTCPPortsAccessible =>
          val out = container.copy(availablePorts = VerifiedContainerConnectivity.HasAvailablePorts(status.availablePorts))
          F.maybeSuspend {
            logger.info(s"Looks good: ${out -> "container"}")
            out
          }

        case Right(last) =>
          val maxAttempts = config.healthCheckMaxAttempts
          val next = attempt + 1
          if (maxAttempts >= next) {
            logger.debug(s"Health check uncertain, retrying $next/$maxAttempts on $container...")
            P.sleep(config.healthCheckInterval).flatMap(_ => await(container, next))
          } else {

            last match {
              case HealthCheckResult.Unavailable =>
                F.fail(new TimeoutException(s"Health checks failed after $maxAttempts attempts, no diagnostics available: $container"))

              case HealthCheckResult.UnavailableWithMeta(unavailablePorts, unverifiedPorts) =>
                import izumi.fundamentals.platform.exceptions.IzThrowable._
                import izumi.fundamentals.platform.strings.IzString._
                val sb = new StringBuilder()
                sb.append(s"Health checks failed after $maxAttempts attempts: $container\n")
                if (unverifiedPorts.nonEmpty) {
                  sb.append(s"Unchecked ports:\n")
                  sb.append(unverifiedPorts.niceList())
                }

                if (unavailablePorts.unavailablePorts.nonEmpty) {
                  val errored = unavailablePorts
                    .unavailablePorts.flatMap {
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
                  sb.append(errored.niceList())
                }

                F.fail(new TimeoutException(sb.toString()))

              case impossible: GoodHealthcheck =>
                F.fail(new TimeoutException(s"BUG: good healthcheck $impossible while health checks failed after $maxAttempts attempts: $container"))
            }

          }

        case Left(t) =>
          F.fail(new RuntimeException(s"$container failed due to exception: ${t.stackTrace}", t))
      }
  }

  private[this] def runReused(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
    logger.info(s"About to start or find container ${config.image}, ${config.pullTimeout -> "max lock retries"}...")
    FileLockMutex.withLocalMutex(logger)(
      s"${config.image.replace("/", "_")}:${config.ports.mkString(";")}",
      waitFor = 200.millis,
      maxAttempts = config.pullTimeout.toSeconds.toInt * 5,
    ) {
      for {
        containers <- F.maybeSuspend {
          /**
            * We will filter out containers only by "running" status if container has any ports to be mapped
            * and by "exited" status also if there are no ports to map to.
            * We don't need to list "exited" containers if ports were defined, because they will not perform a successful port check.
            *
            * Filtering containers like this will allow us to reuse containers that were started and exited eventually.
            * By reusing containers we are usually assumes that container is runs in a daemon, but if container exited successfully -
            * - we can't understood was that container's run belong to the one of the previous test runs, or to the current one.
            * So containers that will exit after doing their job will be reused only in the scope of the current test run.
            */
          val exitedOpt = if (ports.isEmpty) List("exited") else Nil
          val statusFilter = "running" :: exitedOpt
          // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
          try {
            rawClient
              .listContainersCmd()
              .withAncestorFilter(List(config.image).asJava)
              .withStatusFilter(statusFilter.asJava)
              .exec()
              .asScala.toList.sortBy(_.getId)
          } catch {
            case c: Throwable =>
              throw new IntegrationCheckException(Seq(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c))))
          }
        }
        candidate = {
          val portSet = ports.map(_.port).toSet
          val candidates = containers.flatMap {
            c =>
              val inspection = rawClient.inspectContainerCmd(c.getId).exec()
              val networks = inspection.getNetworkSettings.getNetworks.asScala.keys.toList
              val missedNetworks = config.networks.filterNot(n => networks.contains(n.name))
              mapContainerPorts(inspection) match {
                case Left(value) =>
                  logger.info(s"Container ${c.getId} missing ports $value so will not be reused")
                  Seq.empty
                case _ if missedNetworks.nonEmpty =>
                  logger.info(s"Container ${c.getId} missing networks $missedNetworks so will not be reused")
                  Seq.empty
                case Right(value) =>
                  Seq((c, inspection, value))
              }
          }
          if (portSet.nonEmpty) {
            // here we are checking if all ports was successfully mapped
            candidates.find { case (_, _, eports) => portSet.diff(eports.dockerPorts.keySet).isEmpty }
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
        existing <- candidate match {
          case Some((c, inspection, existingPorts)) =>
            val unverified = DockerContainer[T](
              id = ContainerId(c.getId),
              name = inspection.getName,
              connectivity = existingPorts,
              containerConfig = config,
              clientConfig = client.clientConfig,
              availablePorts = VerifiedContainerConnectivity.NoAvailablePorts(),
              hostName = inspection.getConfig.getHostName,
              labels = inspection.getConfig.getLabels.asScala.toMap,
            )
            logger.debug(s"Matching container found: ${config.image}->${unverified.name}, will try to reuse...")
            await(unverified, 0)
          case None =>
            logger.debug(s"No existring container found for ${config.image}, will run...")
            doRun(ports)
        }
      } yield existing
    }
  }

  private[this] def doRun(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
    val allPortLabels = ports.flatMap(p => p.labels).toMap ++ stableLabels
    val baseCmd = rawClient.createContainerCmd(config.image).withLabels(allPortLabels.asJava)

    val volumes = config.mounts.map {
      case Docker.Mount(h, c, true) => new Bind(h, new Volume(c), true)
      case Docker.Mount(h, c, _) => new Bind(h, new Volume(c))
    }

    val portsEnv = ports.map {
      port => port.port.toEnvVariable -> port.binding.getBinding.getHostPortSpec
    }
    val adjustedEnv = portsEnv ++ config.env

    for {
      out <- F.maybeSuspend {
        @nowarn("msg=method.*Bind.*deprecated")
        val cmd = Value(baseCmd)
          .mut(config.name) { case (n, c) => c.withName(n) }
          .mut(ports.nonEmpty)(_.withExposedPorts(ports.map(_.binding.getExposedPort).asJava))
          .mut(ports.nonEmpty)(_.withPortBindings(ports.map(_.binding).asJava))
          .mut(adjustedEnv.nonEmpty)(_.withEnv(adjustedEnv.map { case (k, v) => s"$k=$v" }.toList.asJava))
          .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
          .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.entrypoint.toList.asJava))
          .mut(config.cwd)((cwd, cmd) => cmd.withWorkingDir(cwd))
          .mut(config.user)((user, cmd) => cmd.withUser(user))
          .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
          .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
          .get

        logger.info(s"Going to pull `${config.image}`...")
        rawClient
          .pullImageCmd(config.image)
          .start()
          .awaitCompletion(config.pullTimeout.toMillis, TimeUnit.MILLISECONDS);

        logger.debug(s"Going to create container from image `${config.image}`...")
        val res = cmd.exec()

        logger.debug(s"Going to start container ${res.getId -> "id"}...")
        rawClient.startContainerCmd(res.getId).exec()

        val inspection = rawClient.inspectContainerCmd(res.getId).exec()
        val hostName = inspection.getConfig.getHostName
        val maybeMappedPorts = mapContainerPorts(inspection)

        maybeMappedPorts match {
          case Left(value) =>
            throw new RuntimeException(s"Created container from `${config.image}` with ${res.getId -> "id"}, but ports are missing: $value!")

          case Right(mappedPorts) =>
            val container = DockerContainer[T](
              ContainerId(res.getId),
              inspection.getName,
              hostName,
              inspection.getConfig.getLabels.asScala.toMap,
              mappedPorts,
              config,
              client.clientConfig,
              VerifiedContainerConnectivity.NoAvailablePorts(),
            )
            logger.debug(s"Created $container from ${config.image}...")
            logger.debug(s"Going to attach container ${res.getId -> "id"} to ${config.networks -> "networks"}")
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
      result <- await(out, 0)
    } yield result
  }

  private def mapContainerPorts(inspection: InspectContainerResponse): Either[UnmappedPorts, ReportedContainerConnectivity] = {
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
        val mappings = exposedPort.toSeq.flatMap(p => Option(network.getPorts.getBindings.get(p))).flatten.map {
          port =>
            ServicePort(port.getHostIp, Integer.parseInt(port.getHostPortSpec))
        }
        (containerPort, NonEmptyList.from(mappings))
    }
    val unmapped = ports.collect { case (cp, None) => cp }

    if (unmapped.nonEmpty) {
      Left(UnmappedPorts(unmapped))
    } else {
      val dockerHost = client.rawClientConfig.getDockerHost.getHost
      val networkAddresses = network.getNetworks.asScala.values.toList.map(_.getIpAddress)
      val mapped = ports.collect { case (cp, Some(lst)) => (cp, lst) }

      Right(
        ReportedContainerConnectivity(
          Option(dockerHost),
          networkAddresses,
          mapped.toMap,
        )
      )
    }

  }

  private val stableLabels = Map(
      "distage.reuse" -> shouldReuse(config).toString
    ) ++ client.labels
}

object ContainerResource {
  private final case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])
}
