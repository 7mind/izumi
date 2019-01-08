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
                       )

object NugetOptions {
  def example: NugetOptions = NugetOptions(
    id = "test-library",
    iconUrl = "https://raw.githubusercontent.com/pshirshov/izumi-r2/develop/idealingua/idealingua-runtime-rpc-c-sharp/src/main/resources/unicorn.png",
    requireLicenseAcceptance = false,
  )
}


// https://docs.microsoft.com/en-us/nuget/reference/nuspec
// https://docs.microsoft.com/en-us/nuget/reference/package-versioning
case class CSharpBuildManifest(
                                common: Common,
                                dependencies: List[ManifestDependency],
                                nuget: NugetOptions,
                                layout: CSharpProjectLayout,
                              ) extends BuildManifest

object CSharpBuildManifest {
  def example = CSharpBuildManifest(
    common = BuildManifest.Common.example,
    dependencies = List.empty,
    nuget = NugetOptions.example,
    layout = CSharpProjectLayout.NUGET,
  )
}
