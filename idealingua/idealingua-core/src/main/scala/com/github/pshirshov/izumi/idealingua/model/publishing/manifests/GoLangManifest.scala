package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{Manifest, ManifestDependency, Publisher}


case class GoLangManifest ( name: String,
                            tags: String,
                            description: String,
                            notes: String,
                            publisher: Publisher,
                            version: String,
                            license: String,
                            website: String,
                            copyright: String,
                            dependencies: List[ManifestDependency],
                            repository: String
                          ) extends Manifest

object GoLangManifest {
  def importPrefix(manifest: GoLangManifest): Option[String] =
    if (manifest.repository.isEmpty) None else
    Some(
      if (manifest.repository.endsWith("/")) manifest.repository else manifest.repository + "/"
    )
}