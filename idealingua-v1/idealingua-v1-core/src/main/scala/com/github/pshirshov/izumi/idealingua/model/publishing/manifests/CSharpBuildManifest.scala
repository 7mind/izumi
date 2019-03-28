package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ProjectNamingRule}
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}

sealed trait CSharpProjectLayout

object CSharpProjectLayout {

  final case object NUGET extends CSharpProjectLayout

  final case object PLAIN extends CSharpProjectLayout

}

case class NugetOptions(
                         targetFramework: String,
                         projectNaming: ProjectNamingRule,
                         iconUrl: String,
                         requireLicenseAcceptance: Boolean,
                         dependencies: List[ManifestDependency],
                         testDependencies: List[ManifestDependency],
                       )

object NugetOptions {
  def example: NugetOptions = NugetOptions(
    targetFramework = "net461",
    projectNaming = ProjectNamingRule.example,
    iconUrl = "https://raw.githubusercontent.com/pshirshov/izumi-r2/develop/idealingua/idealingua-runtime-rpc-c-sharp/src/main/resources/unicorn.png",
    requireLicenseAcceptance = false,
    dependencies = List(
      ManifestDependency("WebSocketSharp", "1.0.3-rc11"),
      ManifestDependency("Newtonsoft.Json", "12.0.1"),
    ),
    testDependencies = List(
      ManifestDependency("NUnit", "3.11.0"),
    ),
  )
}

case class CSharpBuildManifest(
                                common: Common,
                                nuget: NugetOptions,
                                layout: CSharpProjectLayout,
                              ) extends BuildManifest

object CSharpBuildManifest {
  def example = CSharpBuildManifest(
    common = BuildManifest.Common.example,
    nuget = NugetOptions.example,
    layout = CSharpProjectLayout.NUGET,
  )
}
