package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}

sealed trait CSharpProjectLayout

object CSharpProjectLayout {

  final case object NUGET extends CSharpProjectLayout

  final case object PLAIN extends CSharpProjectLayout

}

case class NugetOptions(
                         id: String,
                         iconUrl: String,
                         requireLicenseAcceptance: Boolean,

                         /**
                           * Positive value will work as .drop on fully qualified module name
                           * Zero value will leave name untouched
                           * Negative value will work as .takeRight
                           *
                           * Does not apply for layout == PLAIN
                           */
                         dropFQNSegments: Option[Int],
                         projectIdPostfix: Option[String],
                         dependencies: List[ManifestDependency],
                         testDependencies: List[ManifestDependency],
                       )

object NugetOptions {
  def example: NugetOptions = NugetOptions(
    id = "Company.Example.Library",
    iconUrl = "https://raw.githubusercontent.com/pshirshov/izumi-r2/develop/idealingua/idealingua-runtime-rpc-c-sharp/src/main/resources/unicorn.png",
    requireLicenseAcceptance = false,
    dropFQNSegments = Some(-1),
    projectIdPostfix = Some("api"),
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
