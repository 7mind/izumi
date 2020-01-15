package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.docker.examples.{DynamoDocker, PostgresDocker}
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import izumi.fundamentals.platform.properties.EnvVarsCI
import zio.IO

final class DistageTestDockerBIO extends DistageBIOSpecScalatest[IO] {

  // ignore docker tests on CI (nested docker trouble)
  if (!EnvVarsCI.isIzumiCI()) {
    "distage test runner" should {
      "support docker resources" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/1: pg=${service.pg} ddb=${service.ddb} "))
          } yield ()
      }

      "support memoization" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/2: pg=${service.pg} ddb=${service.ddb} "))
          } yield ()
      }
    }
  }

  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Set(
        DIKey.get[DynamoDocker.Container],
        DIKey.get[PostgresDocker.Container],
      ))
  }
}
