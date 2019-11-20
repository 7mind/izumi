package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.integration.docker.examples.{DynamoDocker, PostgresDocker}
import izumi.distage.testkit.services.st.dtest.TestConfig
import izumi.distage.testkit.st.specs.DistageBIOSpecScalatest
import zio.IO

final class DistageTestDockerBIO extends DistageBIOSpecScalatest[IO] {

  "distage test runner" should {
    "support docker resources" in {
      service: PgSvcExample =>
        for {
          _ <- IO(println(s"ports: pg=${service.pg} ddb=${service.ddb} "))
        } yield ()
    }

    "support memoization" in {
      service: PgSvcExample =>
        for {
          _ <- IO(println(s"ports: pg=${service.pg} ddb=${service.ddb} "))
        } yield ()
    }
  }

  override protected def config: TestConfig = {
    TestConfig(
      memoizedKeys = Set(
        DIKey.get[DynamoDocker.Container],
        DIKey.get[PostgresDocker.Container],
      ))
  }
}
