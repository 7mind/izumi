package izumi.distage.testkit.docker.fixtures

import java.util.UUID
import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.model.Docker.{DockerReusePolicy, Mount}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.model.definition.Lifecycle

object ReusedOneshotContainer extends ContainerDef {
  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/alpine:3.17.3",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"sleep 1; echo `date` >> ${CmdContainerModule.stateFilePath}"),
      reuse = DockerReusePolicy.ReuseEnabled,
      autoRemove = false,
      healthCheck = ContainerHealthCheck.exitCodeCheck(),
    )
  }
}

object ReuseCheckContainer extends ContainerDef {
  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/alpine:3.17.3",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"if [[ $$(cat ${CmdContainerModule.stateFilePath} | wc -l | awk '{print $$1}') == 1 ]]; then exit 0; else exit 42; fi"),
      reuse = DockerReusePolicy.ReuseDisabled,
      autoRemove = false,
      healthCheck = ContainerHealthCheck.exitCodeCheck(),
    )
  }
}

object ExitCodeCheckContainer extends ContainerDef {
  override def config: Config = {
    Config(
      registry = Some("public.ecr.aws"),
      image = "docker/library/alpine:3.17.3",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"exit 42"),
      reuse = DockerReusePolicy.ReuseDisabled,
      autoRemove = false,
      healthCheck = ContainerHealthCheck.exitCodeCheck(42),
    )
  }
}

class CmdContainerModule[F[_]: TagK] extends ModuleDef {
  make[ReusedOneshotContainer.Container].fromResource {
    ReusedOneshotContainer.make[F]
  }

  make[Lifecycle[F, ReuseCheckContainer.Container]].from(ReuseCheckContainer.make[F])
  make[ContainerResource[F, ExitCodeCheckContainer.Tag]].from(ExitCodeCheckContainer.make[F])
}

object CmdContainerModule {
  def apply[F[_]: TagK]: CmdContainerModule[F] = new CmdContainerModule[F]

  private val runId: String = UUID.randomUUID().toString

  val stateFileMount: Mount = Mount("/tmp/", "/tmp/docker-test/")
  val stateFilePath: String = s"/tmp/docker-test/docker-test-$runId.txt"
}
