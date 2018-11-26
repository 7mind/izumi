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
                               useRepositoryFolders: Boolean = false,
                          ) extends BuildManifest

object GoLangBuildManifest {
  def importPrefix(manifest: GoLangBuildManifest): Option[String] =
    if (manifest.repository.isEmpty) None else
    Some(
      if (manifest.repository.endsWith("/")) manifest.repository else manifest.repository + "/"
    )
}