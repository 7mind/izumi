package izumi.distage.testkit.distagesuite.parallel

import izumi.fundamentals.platform.functional.Identity
import izumi.distage.testkit.model.TestConfig
import izumi.logstage.api.Log

// JVM-only Identity tests - use QuasiTemporal which requires blocking on Identity
final class DistageParallelLevelTestId1 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId2 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId3 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId4 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId5 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter)
final class DistageParallelLevelTestId6 extends DistageParallelLevelTest[Identity](DistageParallelLevelTest.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}
