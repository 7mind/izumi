package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}


case class ScalaBuildManifest(name: String,
                              tags: String,
                              description: String,
                              notes: String,
                              publisher: Publisher,
                              version: String,
                              license: String,
                              website: String,
                              copyright: String,
                              dependencies: List[ManifestDependency]
                          ) extends BuildManifest

object ScalaBuildManifest {
}