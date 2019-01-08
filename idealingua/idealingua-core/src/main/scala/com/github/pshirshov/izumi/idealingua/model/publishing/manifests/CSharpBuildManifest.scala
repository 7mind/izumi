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
    iconUrl = "",
    requireLicenseAcceptance = false,
  )
}


// https://docs.microsoft.com/en-us/nuget/reference/nuspec
// https://docs.microsoft.com/en-us/nuget/reference/package-versioning
case class CSharpBuildManifest(
                                common: Common,
                                dependencies: List[ManifestDependency],
                                nuget: NugetOptions,
                              ) extends BuildManifest

object CSharpBuildManifest {
  def example = CSharpBuildManifest(
    common = BuildManifest.Common.example,
    dependencies = List.empty,
    nuget = NugetOptions.example,
  )
}
