package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}


case class GoLangBuildManifest(name: String,
                               tags: String,
                               description: String,
                               notes: String,
                               publisher: Publisher,
                               version: String,
                               license: String,
                               website: String,
                               copyright: String,
                               dependencies: List[ManifestDependency],
                               repository: String,
                               useRepositoryFolders: Boolean,
                              ) extends BuildManifest

object GoLangBuildManifest {
  def default: GoLangBuildManifest = GoLangBuildManifest(
    name = "TestBuild",
    tags = "",
    description = "Test Description",
    notes = "",
    publisher = Publisher("Test Publisher Name", "test_publisher_id"),
    version = "0.0.0",
    license = "MIT",
    website = "http://project.website",
    copyright = "Copyright (C) Test Inc.",
    dependencies = List(ManifestDependency("github.com/gorilla/websocket", "")),
    repository = "github.com/TestCompany/TestRepo",
    useRepositoryFolders = false,
  )

  def importPrefix(manifest: GoLangBuildManifest): String = {
    if (!manifest.useRepositoryFolders || manifest.repository.isEmpty) {
      ""
    } else {
      if (manifest.repository.endsWith("/")) manifest.repository else manifest.repository + "/"
    }
  }
}
