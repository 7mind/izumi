package izumi.distage.testkit.modulefiltering
import io.github.classgraph.ClassInfo

final class SbtModuleFilteringTest extends SbtModuleFilteringPoisonPillTest {
  override protected def _sbtReportFilteredOutTest(cls: ClassInfo, fileClassPathElem: String, firstTestClassesClassPathElem: String): Unit = {
    ()
  }
}
