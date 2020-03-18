package izumi.distage.testkit.docker

import java.util.UUID

import distage.DIKey
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import izumi.fundamentals.platform.properties.EnvVarsCI
import izumi.logstage.api.Log
import zio.IO

// this tests needed to check mutex for reusable containers during parallel test runs
class DistageTestDockerBIO extends DistageBIOSpecScalatest[IO] {
  // ignore docker tests on CI (nested docker trouble)
  if (!EnvVarsCI.isIzumiCI()) {
    "distage test runner should start only one container for reusable" should {
      "support docker resources" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/1: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
          } yield ()
      }

      "support memoization" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/2: pg=${service.pg} ddb=${service.ddb} kafka=${service.kafka} cs=${service.cs}"))
          } yield ()
      }
    }
  }

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(DIKey.get[PgSvcExample]),
      parallelTests = true,
      parallelEnvs = true,
      testRunnerLogLevel = Log.Level.Debug
    )
  }
}

final class DistageTestDockerBIO1 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO2 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO3 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO4 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO5 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO6 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO7 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO8 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO9 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO10 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO11 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO12 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO13 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO14 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }
final class DistageTestDockerBIO15 extends DistageTestDockerBIO { override protected def config: TestConfig = DistageTestDockerBIO.randomEnv(super.config) }

object DistageTestDockerBIO {
  def randomEnv(testConfig: TestConfig): TestConfig = testConfig.copy(
    moduleOverrides = testConfig.moduleOverrides overridenBy new ModuleDef { make[UUID].fromValue(UUID.randomUUID()) }
  )
}
