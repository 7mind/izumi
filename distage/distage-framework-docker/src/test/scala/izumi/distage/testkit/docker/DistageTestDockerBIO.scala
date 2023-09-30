package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.model.definition.Lifecycle
import izumi.distage.testkit.model.TestConfig.Parallelism
import izumi.distage.testkit.docker.fixtures.{PgSvcExample, ReuseCheckContainer}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec2
import izumi.logstage.api.Log
import logstage.LogIO2
import zio.{IO, ZIO}

// these tests need to check mutex for reusable containers during parallel test runs
abstract class DistageTestDockerBIO extends Spec2[IO] {

  override protected def config: TestConfig = super.config.copy(
    memoizationRoots = Set(DIKey[PgSvcExample]),
    parallelTests = Parallelism.Unlimited,
    parallelEnvs = Parallelism.Unlimited,
    logLevel = Log.Level.Info,
  )

  "distage test runner should start only one container for reusable" should {

    "support docker resources" in {
      // TODO: additionally check flyway outcome with doobie
      (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, _], ReuseCheckContainer.Container], log: LogIO2[IO]) =>
        println(s"ports/1: pg=${service.pg} pgfw=${service.pgfw} ddb=${service.ddb} kafka=${service.kafka}/${service.kafkaKraft}/${service.kafkaTwoFace} cs=${service.cs}")
        for {
          _ <- log.info(s"ports/1: pg=${service.pg} pgfw=${service.pgfw} ddb=${service.ddb} kafka=${service.kafka}/${service.kafkaKraft}/${service.kafkaTwoFace} cs=${service.cs}")
          // a new alpine container is spawned every time here
          _ <- verifier.use(_ => ZIO.unit)
        } yield ()
    }

    "support memoization" in {
      (service: PgSvcExample, verifier: Lifecycle[IO[Throwable, _], ReuseCheckContainer.Container], log: LogIO2[IO]) =>
        for {
          _ <- log.info(s"ports/2: pg=${service.pg} pgfw=${service.pgfw} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}")
          // a new alpine container is spawned every time here
          _ <- verifier.use(_ => ZIO.unit)
        } yield ()
    }

  }
}

final class DistageTestDockerBIOFirstEnv1 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv2 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv3 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv4 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv5 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv6 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv7 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv8 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv9 extends DistageTestDockerBIO
final class DistageTestDockerBIOFirstEnv10 extends DistageTestDockerBIO

abstract class DistageTestDockerBIOSecondEnv extends DistageTestDockerBIO {
  override protected def config: TestConfig = super.config.copy(
    // force break env reuse and test global docker reuse across envs by forking the env (by changing env log level)
    logLevel = Log.Level.Warn
  )
}
final class DistageTestDockerBIOSecondEnv1 extends DistageTestDockerBIOSecondEnv
final class DistageTestDockerBIOSecondEnv2 extends DistageTestDockerBIOSecondEnv
final class DistageTestDockerBIOSecondEnv3 extends DistageTestDockerBIOSecondEnv
final class DistageTestDockerBIOSecondEnv4 extends DistageTestDockerBIOSecondEnv
final class DistageTestDockerBIOSecondEnv5 extends DistageTestDockerBIOSecondEnv

abstract class DistageTestDockerBIOThirdEnv extends DistageTestDockerBIO {
  override protected def config: TestConfig = super.config.copy(
    // force break env reuse and test global docker reuse across envs by forking the env (by changing env log level)
    logLevel = Log.Level.Error
  )
}

final class DistageTestDockerBIOThirdEnv1 extends DistageTestDockerBIOThirdEnv
final class DistageTestDockerBIOThirdEnv2 extends DistageTestDockerBIOThirdEnv
final class DistageTestDockerBIOThirdEnv3 extends DistageTestDockerBIOThirdEnv
final class DistageTestDockerBIOThirdEnv4 extends DistageTestDockerBIOThirdEnv
final class DistageTestDockerBIOThirdEnv5 extends DistageTestDockerBIOThirdEnv
