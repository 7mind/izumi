package izumi.distage.testkit.distagesuite.sequential

import izumi.fundamentals.platform.functional.Identity
import izumi.distage.testkit.model.TestConfig
import izumi.logstage.api.Log

// JVM-only Identity tests - use QuasiTemporal which requires blocking on Identity
final class DistageSequentialSuitesTestId1 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId2 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId3 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId4 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId5 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter)
final class DistageSequentialSuitesTestId6 extends DistageSequentialSuitesTest[Identity](DistageSequentialSuitesTest.idCounter) {
  override protected def config: TestConfig = super.config.copy(logLevel = Log.Level.Info)
}
