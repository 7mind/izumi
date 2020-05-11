package izumi.distage.docker

import java.util.concurrent.{TimeUnit, TimeoutException}

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.ghik.silencer.silent
import izumi.distage.docker.Docker._
import izumi.distage.docker.healthcheck.ContainerHealthCheck.{HealthCheckResult, VerifiedContainerConnectivity}
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.functional.Value
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.network.IzSockets
import izumi.logstage.api.IzLogger

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

case class ContainerResource[F[_], T](
                                       config: Docker.ContainerConfig[T],
                                       clientw: DockerClientWrapper[F],
                                       logger: IzLogger,
                                     )(
                                       implicit
                                       val F: DIEffect[F],
                                       val P: DIEffectAsync[F]
                                     ) extends DIResource[F, DockerContainer[T]] {

  import ContainerResource._

  private[this] val client = clientw.rawClient

  private[this] def toExposedPort(port: DockerPort): ExposedPort = {
    port match {
      case DockerPort.TCP(number) =>
        ExposedPort.tcp(number)
      case DockerPort.UDP(number) =>
        ExposedPort.udp(number)
    }
  }

  private[this] def shouldReuse(config: Docker.ContainerConfig[T]): Boolean = {
    config.reuse && clientw.clientConfig.allowReuse
  }

  override def acquire: F[DockerContainer[T]] = F.suspendF {
    val ports = config.ports.map {
      containerPort =>
        val local = IzSockets.temporaryLocalPort()
        val bp = toExposedPort(containerPort)
        val binding = new PortBinding(Ports.Binding.bindPort(local), bp)
        val stableLabels = Map(
          "distage.reuse" -> shouldReuse(config).toString,
          s"distage.port.${containerPort.protocol}.${containerPort.number}.defined" -> "true",
        )
        val labels = Map(
          s"distage.port.${containerPort.protocol}.${containerPort.number}" -> local.toString,
        )
        PortDecl(containerPort, local, binding, stableLabels ++ labels)
    }

    if (shouldReuse(config)) {
      runReused(ports)
    } else {
      doRun(ports)
    }
  }

  override def release(resource: DockerContainer[T]): F[Unit] = {
    if (!shouldReuse(resource.containerConfig)) {
      clientw.destroyContainer(resource.id)
    } else {
      F.unit
    }
  }


  def await(container: DockerContainer[T], attempt: Int): F[DockerContainer[T]] = {
    F.maybeSuspend {
      logger.debug(s"Awaiting until alive: $container...")
      try {
        val status = client.inspectContainerCmd(container.id.name).exec()
        if (status.getState.getRunning) {
          logger.debug(s"Trying healthcheck on running $container...")
          Right(config.healthCheck.check(logger, container))
        } else {
          Left(new RuntimeException(s"Container exited: ${container.id}, full status: $status"))
        }
      } catch {
        case t: Throwable =>
          Left(t)
      }
    }.flatMap {
      case Right(HealthCheckResult.Ignored) =>
        F.maybeSuspend {
          logger.info(s"Continuing without port checks: $container")
          container
        }

      case Right(status: HealthCheckResult.PortStatus) =>
        if (status.requiredPortsAccessible) {
          val out = container.copy(availablePorts = status.availablePorts)
          F.maybeSuspend {
            logger.info(s"Looks good: ${out -> "container"}")
            out
          }
        } else {
          val max = config.healthCheckMaxAttempts
          val next = attempt + 1
          if (max >= next) {
            logger.debug(s"Healthcheck uncertain, retrying $next/$max on $container...")
            P.sleep(config.healthCheckInterval).flatMap(_ => await(container, next))
          } else {
            F.fail(new TimeoutException(s"Failed to start after $max attempts: $container"))
          }
        }


      case Left(t) =>
        F.fail(new RuntimeException(s"Container failed: ${container.id}", t))
    }
  }

  private[this] def runReused(ports: Seq[PortDecl]): F[DockerContainer[T]] = {
    logger.info(s"About to start or find container ${config.image}, ${config.pullTimeout -> "max lock retries"}...")
    FileLockMutex.withLocalMutex(logger)(
      s"${config.image.replace("/", "_")}:${config.ports.mkString(";")}",
      waitFor = 200.millis,
      maxAttempts = config.pullTimeout.toSeconds.toInt
    ) {
      for {
        containers <- F.maybeSuspend {
          // FIXME: temporary hack to allow missing containers to skip tests (happens when both DockerWrapper & integration check that depends on Docker.Container are memoized)
          try {
            client
              .listContainersCmd()
              .withAncestorFilter(List(config.image).asJava)
              .withStatusFilter(List("running").asJava)
              .exec()
              .asScala.toList.sortBy(_.getId)
          } catch {
            case c: Throwable =>
              throw new IntegrationCheckException(Seq(ResourceCheck.ResourceUnavailable(c.getMessage, Some(c))))
          }
        }

        candidates = {
          val portSet = ports.map(_.port).toSet
          containers.iterator.flatMap {
            c =>
              val inspection = client.inspectContainerCmd(c.getId).exec()
              mapContainerPorts(inspection) match {
                case Left(value) =>
                  logger.info(s"Container ${c.getId} missing ports $value so will not be reused")
                  Seq.empty
                case Right(value) =>
                  Seq((c, inspection, value))
              }
          }.find {
            case (_, _, eports) =>
              portSet.diff(eports.dockerPorts.keySet).isEmpty
          }
        }
        existing <- candidates match {
          case Some((c, inspection, existingPorts)) =>
            val unverified = DockerContainer[T](
              id = ContainerId(c.getId),
              name = inspection.getName,
              connectivity = existingPorts,
              containerConfig = config,
              clientConfig = clientw.clientConfig,
              availablePorts = VerifiedContainerConnectivity.empty,
              hostName = inspection.getConfig.getHostName,
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
    val allPortLabels = ports.flatMap(p => p.labels).toMap
    val baseCmd = client
      .createContainerCmd(config.image)
      .withLabels((clientw.labels ++ allPortLabels).asJava)

    val volumes = config.mounts.map {
      case Docker.Mount(h, c, true) => new Bind(h, new Volume(c), true)
      case Docker.Mount(h, c, _) => new Bind(h, new Volume(c))
    }

    for {
      out <- F.maybeSuspend {
        @silent("method.*Bind.*deprecated")
        val cmd = Value(baseCmd)
          .mut(config.name) { case (n, c) => c.withName(n) }
          .mut(ports.nonEmpty)(_.withExposedPorts(ports.map(_.binding.getExposedPort).asJava))
          .mut(ports.nonEmpty)(_.withPortBindings(ports.map(_.binding).asJava))
          .mut(config.env.nonEmpty)(_.withEnv(config.env.map {
            case (k, v) => s"$k=$v"
          }.toList.asJava))
          .mut(config.cmd.nonEmpty)(_.withCmd(config.cmd.toList.asJava))
          .mut(config.entrypoint.nonEmpty)(_.withEntrypoint(config.cmd.toList.asJava))
          .mut(config.cwd)((cwd, cmd) => cmd.withWorkingDir(cwd))
          .mut(config.user)((user, cmd) => cmd.withUser(user))
          .mut(volumes.nonEmpty)(_.withVolumes(volumes.map(_.getVolume).asJava))
          .mut(volumes.nonEmpty)(_.withBinds(volumes.toList.asJava))
          .get

        logger.info(s"Going to pull `${config.image}`...")
        client
          .pullImageCmd(config.image)
          .start()
          .awaitCompletion(config.pullTimeout.toMillis, TimeUnit.MILLISECONDS);

        logger.debug(s"Going to create container from image `${config.image}`...")
        val res = cmd.exec()

        logger.debug(s"Going to start container ${res.getId -> "id"}...")
        client.startContainerCmd(res.getId).exec()

        val inspection = client.inspectContainerCmd(res.getId).exec()
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
              mappedPorts,
              config,
              clientw.clientConfig,
              VerifiedContainerConnectivity.empty,
            )
            logger.debug(s"Created $container from ${config.image}...")
            logger.debug(s"Going to attach container ${res.getId -> "id"} to ${config.networks -> "networks"}")
            config.networks.foreach {
              network =>
                client
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

  private def mapContainerPorts(inspection: InspectContainerResponse): Either[UnmappedPorts, ContainerConnectivity] = {
    val network = inspection.getNetworkSettings

    val ports = config.ports.map {
      containerPort =>
        val mappings = for {
          exposed <- Option(network.getPorts.getBindings.get(toExposedPort(containerPort))).toSeq
          port <- exposed
        } yield {
          ServicePort(port.getHostIp, Integer.parseInt(port.getHostPortSpec))
        }

        (containerPort, NonEmptyList.from(mappings))
    }
    val unmapped = ports.collect { case (cp, None) => cp }

    if (unmapped.nonEmpty) {
      Left(UnmappedPorts(unmapped))
    } else {
      val dockerHost = clientw.rawClientConfig.getDockerHost.getHost
      val networkAddresses = network.getNetworks.asScala.values.toList.map(_.getIpAddress)
      val mapped = ports.collect { case (cp, Some(lst)) => (cp, lst) }

      Right(ContainerConnectivity(
        Option(dockerHost),
        networkAddresses,
        mapped.toMap,
      ))
    }

  }

}

object ContainerResource {

  private final case class PortDecl(port: DockerPort, localFree: Int, binding: PortBinding, labels: Map[String, String])

}