package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*

import sbt.*
import sbt.Keys.*

private[sbt] object HostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    distageCatalogueId := Def.uncached { DistageHostPolicy.catalogueId(definedTests.value) },
    testOptions := Def.uncached { DistageHostPolicy.withIdentity(testOptions.value, DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)) },
    testQuick / testFilter := Def.uncached { DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testSelected / testFilter).value, streams.value.log) },
  )
}
