package izumi.distage.testkit.docker

import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.testkit.docker.fixtures.ExitCodeCheckContainer
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import zio.{IO, ZIO}

final class ExitCodeCheckTest extends Spec2[IO] with AssertZIO {

  "Exit code check" should {

    "Succeed on correct exit code" in {
      (checkingContainer: ContainerResource[IO[Throwable, _], ExitCodeCheckContainer.Tag]) =>
        checkingContainer
          .use(_ => ZIO.unit)
    }

    "Fail on incorrect exit code" in {
      (checkingContainer: ContainerResource[IO[Throwable, _], ExitCodeCheckContainer.Tag]) =>
        for {
          r <- checkingContainer
            .copy(config =
              checkingContainer.config.copy(
                healthCheck = ContainerHealthCheck.exitCodeCheck(1)
              )
            )
            .use(_ => ZIO.unit)
            .either
          Left(error) = r: @unchecked
          _ <- assertIO(error.getMessage contains "Code=42, expected=1")
        } yield ()
    }
  }

}
