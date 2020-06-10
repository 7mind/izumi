package izumi.distage.testkit.docker.fixtures
import java.nio.file.{Files, Path}

import distage.{ModuleDef, TagK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.Mount

object CmdContainer extends ContainerDef {
  override def config: Config = {
    Config(
      image = "alpine:3.12.0",
      ports = Seq(),
      mounts = Seq(Mount(CmdContainerModule.mountFile.toAbsolutePath.toString, "/docker-test.txt")),
      entrypoint = Seq("sh", "-c", "echo foo >> /docker-test.txt"),
      reuse = true,
    )
  }
}

class CmdContainerModule[F[_]: TagK] extends ModuleDef {
  make[CmdContainer.Container].fromResource {
    CmdContainer.make[F]
  }
}

object CmdContainerModule {
  def apply[F[_]: TagK]: CmdContainerModule[F] = new CmdContainerModule[F]
  val mountFile: Path = Files.createTempFile("docker-test", ".txt")
  mountFile.toFile.createNewFile()
  mountFile.toFile.deleteOnExit()
  def checkFileForContent(): Unit = {
    val lines = Files.readAllLines(mountFile)
    assert(lines.size() == 1)
  }
}
