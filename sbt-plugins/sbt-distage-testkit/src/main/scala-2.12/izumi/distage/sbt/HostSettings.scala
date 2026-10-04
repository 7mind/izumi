package izumi.distage.sbt

import izumi.distage.sbt.DistageTestkitPlugin.autoImport._

import sbt._
import sbt.Keys._

private[sbt] object HostSettings {
  def settings: Seq[Def.Setting[?]] = Seq(
    distageBuildId := thisProjectRef.value.build.toString,
    distageTargetId := thisProjectRef.value.project + "/" + configuration.value.name,
    testFrameworks ~= { frameworks => if (frameworks.contains(DistageHostPolicy.framework)) frameworks else frameworks :+ DistageHostPolicy.framework },
    distageCatalogueId := DistageHostPolicy.catalogueId(definedTests.value),
    testOptions := DistageHostPolicy.withIdentity(testOptions.value, DistageHostPolicy.arguments(distageBuildId.value, distageTargetId.value, distageCatalogueId.value)),
    testQuick / testFilter := DistageHostPolicy.conservativeFilter(definedTests.value, (testQuick / testFilter).value, (testOnly / testFilter).value, streams.value.log),
  )
}
