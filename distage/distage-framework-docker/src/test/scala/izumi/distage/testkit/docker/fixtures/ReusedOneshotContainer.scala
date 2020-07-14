package izumi.distage.testkit.docker.fixtures

import java.util.UUID

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{DockerReusePolicy, Mount}
import izumi.distage.model.definition.DIResource.DIResourceBase

object ReusedOneshotContainer extends ContainerDef {
  override def config: Config = {
    Config(
      image = "alpine:3.12.0",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"echo `date` >> ${CmdContainerModule.stateFilePath}"),
      reuse = DockerReusePolicy.KeepAliveOnExitAndReuse,
    )
  }
}

object ReuseCheckContainer extends ContainerDef {
  override def config: Config = {
    Config(
      image = "alpine:3.11.0",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"if [[ $$(cat ${CmdContainerModule.stateFilePath} | wc -l | awk '{print $$1}') == 1 ]]; then exit 0; else exit 42; fi"),
      reuse = DockerReusePolicy.KillOnExit,
    )
  }
}

class CmdContainerModule[F[_]: TagK] extends ModuleDef {
  make[ReusedOneshotContainer.Container].fromResource {
    ReusedOneshotContainer.make[F]
  }

  make[DIResourceBase[F, ReuseCheckContainer.Container]].from(ReuseCheckContainer.make[F])
}

object CmdContainerModule {
  def apply[F[_]: TagK]: CmdContainerModule[F] = new CmdContainerModule[F]

  private val runId = UUID.randomUUID().toString

  val stateFileMount: Mount = Mount("/tmp/", s"/tmp/docker-test/")
  val stateFilePath: String = s"/tmp/docker-test/docker-test-${CmdContainerModule.runId}.txt"
}
