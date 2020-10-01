package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.model.definition.Lifecycle
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.docker.fixtures.{PgSvcExample, ReuseCheckContainer}
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import izumi.fundamentals.platform.properties.EnvVarsCI
import izumi.logstage.api.Log
import zio.IO

// this tests needed to check mutex for reusable containers during parallel test runs
abstract class DistageTestDockerBIO extends DistageBIOSpecScalatest[IO] {

  if (!EnvVarsCI.isIzumiCI()) {

    "distage test runner should start only one container for reusable" should {
      "support docker resources" in {
        (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, ?], ReuseCheckContainer.Container]) =>
          for {
            _ <- IO(println(s"ports/1: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
            _ <- verifier.use(_ => IO.unit)
          } yield ()
      }

      "support memoization" in {
        (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, ?], ReuseCheckContainer.Container]) =>
          for {
            _ <- IO(println(s"ports/2: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
            _ <- verifier.use(_ => IO.unit)
          } yield ()
      }
    }

  }

  override protected def config: TestConfig = super
    .config.copy(
      memoizationRoots = Set(DIKey.get[PgSvcExample]),
      parallelTests = ParallelLevel.Unlimited,
      parallelEnvs = ParallelLevel.Unlimited,
      logLevel = Log.Level.Info,
    )

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
