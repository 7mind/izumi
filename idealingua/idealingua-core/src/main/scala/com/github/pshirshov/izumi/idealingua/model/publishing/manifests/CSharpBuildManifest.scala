package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}


case class CSharpBuildManifest(
                                common: Common,
                                dependencies: List[ManifestDependency],
                                id: String,
                                iconUrl: String,
                                requireLicenseAcceptance: Boolean
                              ) extends BuildManifest

object CSharpBuildManifest {
  def default = CSharpBuildManifest(
    common = BuildManifest.Common.default,
    dependencies = List.empty,
    id = "test-library",
    iconUrl = "",
    requireLicenseAcceptance = false,
  )
}
