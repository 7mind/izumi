package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.model.definition.Lifecycle
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.docker.fixtures.{PgSvcExample, ReuseCheckContainer}
import izumi.distage.testkit.scalatest.Spec2
import izumi.fundamentals.platform.build.MacroParameters
import izumi.logstage.api.Log
import logstage.LogIO2
import zio.IO

// these tests need to check mutex for reusable containers during parallel test runs
abstract class DistageTestDockerBIO extends Spec2[IO] {

  override protected def config: TestConfig = super.config.copy(
    memoizationRoots = Set(DIKey[PgSvcExample]),
    parallelTests = ParallelLevel.Unlimited,
    parallelEnvs = ParallelLevel.Unlimited,
    logLevel = Log.Level.Info,
  )

  def insideCI: Boolean = MacroParameters.sbtIsInsideCI().getOrElse(false)

  if (!insideCI) {

    "distage test runner should start only one container for reusable" should {

      "support docker resources" in {
        // TODO: additionally check flyway outcome with doobie
        (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, `?`], ReuseCheckContainer.Container], log: LogIO2[IO]) =>
          for {
            _ <- log.info(s"ports/1: pg=${service.pg} pgfw=${service.pgfw} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}")
            _ <- verifier.use(_ => IO.unit)
          } yield ()
      }

      "support memoization" in {
        (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, `?`], ReuseCheckContainer.Container], log: LogIO2[IO]) =>
          for {
            _ <- log.info(s"ports/2: pg=${service.pg} pgfw=${service.pgfw} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}")
            _ <- verifier.use(_ => IO.unit)
          } yield ()
      }

    }

  }

}

final class DistageTestDockerBIO1 extends DistageTestDockerBIO
final class DistageTestDockerBIO2 extends DistageTestDockerBIO
final class DistageTestDockerBIO3 extends DistageTestDockerBIO
final class DistageTestDockerBIO4 extends DistageTestDockerBIO
final class DistageTestDockerBIO5 extends DistageTestDockerBIO
final class DistageTestDockerBIO6 extends DistageTestDockerBIO
final class DistageTestDockerBIO7 extends DistageTestDockerBIO
final class DistageTestDockerBIO8 extends DistageTestDockerBIO
final class DistageTestDockerBIO9 extends DistageTestDockerBIO
final class DistageTestDockerBIO10 extends DistageTestDockerBIO
final class DistageTestDockerBIO11 extends DistageTestDockerBIO
final class DistageTestDockerBIO12 extends DistageTestDockerBIO
final class DistageTestDockerBIO13 extends DistageTestDockerBIO
final class DistageTestDockerBIO14 extends DistageTestDockerBIO
final class DistageTestDockerBIO15 extends DistageTestDockerBIO
final class DistageTestDockerBIOSecondEnv extends DistageTestDockerBIO {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Warn)
}
final class DistageTestDockerBIOThirdEnv extends DistageTestDockerBIO {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Error)
}
