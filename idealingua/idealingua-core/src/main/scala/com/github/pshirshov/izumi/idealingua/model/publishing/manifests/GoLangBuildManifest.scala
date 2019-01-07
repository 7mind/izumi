package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}


case class GoLangBuildManifest(
                                common: Common,
                                dependencies: List[ManifestDependency],
                                repository: String,
                                useRepositoryFolders: Boolean,
                              ) extends BuildManifest

object GoLangBuildManifest {
  def example: GoLangBuildManifest = {
    GoLangBuildManifest(
      common = BuildManifest.Common.example,
      dependencies = List(ManifestDependency("github.com/gorilla/websocket", "")),
      repository = "github.com/TestCompany/TestRepo",
      useRepositoryFolders = false,
    )
  }

  def importPrefix(manifest: GoLangBuildManifest): String = {
    if (!manifest.useRepositoryFolders || manifest.repository.isEmpty) {
      ""
    } else {
      if (manifest.repository.endsWith("/")) manifest.repository else manifest.repository + "/"
    }
  }
}
