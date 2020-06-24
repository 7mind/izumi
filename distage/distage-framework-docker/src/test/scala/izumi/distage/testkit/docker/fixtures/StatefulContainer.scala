package izumi.distage.testkit.docker.fixtures

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.Mount
import izumi.distage.model.definition.DIResource.DIResourceBase

object StatefulContainer extends ContainerDef {
  override def config: Config = {
    Config(
      image = "alpine:3.12.0",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"echo `date` >> ${CmdContainerModule.stateFilePath}"),
      reuse = true,
    )
  }
}

object StatefulCheckContainer extends ContainerDef {
  override def config: Config = {
    Config(
      image = "alpine:3.12.0",
      ports = Seq(),
      mounts = Seq(CmdContainerModule.stateFileMount),
      entrypoint = Seq("sh", "-c", s"if [[ $$(cat ${CmdContainerModule.stateFilePath} | wc -l | awk '{print $$1}') == 1 ]]; then exit 0; else exit 42; fi"),
//      entrypoint = Seq("sh", "-c", s"exit 1"),
      reuse = false,
    )
  }
}

class CmdContainerModule[F[_]: TagK] extends ModuleDef {
  make[StatefulContainer.Container].fromResource {
    StatefulContainer.make[F]
  }

  make[DIResourceBase[F, StatefulCheckContainer.Container]].from(StatefulCheckContainer.make[F])
}

object CmdContainerModule {
  def apply[F[_]: TagK]: CmdContainerModule[F] = new CmdContainerModule[F]

  private val runId = System.nanoTime().toString

  val stateFileMount: Mount = Mount("/tmp/", s"/tmp/docker-test/")
  val stateFilePath: String = s"/tmp/docker-test/docker-test-${CmdContainerModule.runId}.txt"
}
