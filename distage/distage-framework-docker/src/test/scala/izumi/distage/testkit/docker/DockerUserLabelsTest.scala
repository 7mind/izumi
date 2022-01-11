package izumi.distage.testkit.docker

import distage.ModuleDef
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.docker.DockerUserLabelsTest.*
import izumi.distage.testkit.scalatest.Spec2
import izumi.functional.bio.F
import zio.{IO, Task}

object DockerUserLabelsTest {
  object PostgresTestDocker extends ContainerDef {
    val primaryPort: DockerPort = DockerPort.TCP(5432)

    override def config: Config = {
      Config(
        image = "library/postgres:12.3",
        ports = Seq(primaryPort),
        env = Map("POSTGRES_PASSWORD" -> "postgres"),
        userTags = Map("user.specific.tag" -> "test.tag"),
        healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(primaryPort, "postgres", "postgres"),
      )
    }
  }

  val taggedDockerModule = new ModuleDef {
    make[PostgresTestDocker.Container].fromResource {
      PostgresTestDocker.make[Task]
    }
  }
}

final class DockerUserLabelsTest extends Spec2[IO] {
  override protected def config: TestConfig = super.config.copy(moduleOverrides = taggedDockerModule)

  "Container resource" should {
    "apply user tags" in {
      (labeledContainer: PostgresTestDocker.Container) =>
        for {
          labels <- F.sync(labeledContainer.labels)
          _ = assert(labels.get("user.specific.tag").contains("test.tag"))
        } yield ()
    }
  }
}
