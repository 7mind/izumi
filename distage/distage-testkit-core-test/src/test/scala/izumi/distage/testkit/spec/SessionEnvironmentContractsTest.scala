package izumi.distage.testkit.spec

import izumi.distage.testkit.runner.spec.AsyncWordSpec

final class SessionEnvironmentContractsTest extends AsyncWordSpec {
  "Session environment contracts" should {
    "preserve construction, memoization, ownership and joined completion" in {
      SessionEnvironmentFixturePlatform.runFuture(SessionEnvironmentFixtures.runContracts, sessionExecutionContext)
    }
  }
}
