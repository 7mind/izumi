package izumi.distage.testkit.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.{Frame, StreamType}
import distage.ModuleDef
import izumi.distage.docker.bundled.PostgresFlyWayDocker
import izumi.distage.docker.impl.DockerClientWrapper
import izumi.distage.docker.model.Docker
import izumi.distage.docker.model.Docker.DockerReusePolicy
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import zio.{IO, Task, ZIO}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

final class PostgresFlyWayDockerTest extends Spec2[IO] with AssertZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[Docker.ClientConfig].fromValue(Docker.ClientConfig(globalReuse = DockerReusePolicy.ReuseDisabled))
    }
  )

  "PostgresFlyWayDocker" should {

    "apply the migrations from the sql resource directory" in {
      (postgres: PostgresFlyWayDocker.Container, cfg: PostgresFlyWayDocker.Cfg, client: DockerClientWrapper[Task]) =>
        for {
          scripts <- ZIO.attempt(psql(client, postgres, cfg, s"select script from ${cfg.schema}.flyway_schema_history where type = 'SQL' and success"))
          _ <- assertIO(scripts.contains("V2__AddExampleTable.sql"))
        } yield ()
    }

  }

  private def psql(client: DockerClientWrapper[Task], postgres: PostgresFlyWayDocker.Container, cfg: PostgresFlyWayDocker.Cfg, query: String): List[String] = {
    val exec = client.rawClient
      .execCreateCmd(postgres.id.name)
      .withAttachStdout(true)
      .withAttachStderr(true)
      .withCmd("psql", "-U", cfg.user, "-d", cfg.database, "-v", "ON_ERROR_STOP=1", "-tAc", query)
      .exec()
    val stdout = new ByteArrayOutputStream()
    val stderr = new ByteArrayOutputStream()
    val output = new ResultCallback.Adapter[Frame] {
      override def onNext(frame: Frame): Unit = {
        if (frame.getStreamType == StreamType.STDERR) stderr.write(frame.getPayload) else stdout.write(frame.getPayload)
      }
    }
    client.rawClient.execStartCmd(exec.getId).exec(output).awaitCompletion()
    val exitCode = client.rawClient.inspectExecCmd(exec.getId).exec().getExitCodeLong
    assert(exitCode == 0L, s"psql exited with $exitCode: ${stderr.toString(StandardCharsets.UTF_8.name())}")
    stdout.toString(StandardCharsets.UTF_8.name()).split('\n').toList.map(_.trim).filter(_.nonEmpty)
  }

}
