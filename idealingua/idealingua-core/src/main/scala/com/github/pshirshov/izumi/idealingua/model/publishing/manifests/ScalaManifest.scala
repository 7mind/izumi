package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{Manifest, ManifestDependency, Publisher}


case class ScalaManifest (  name: String,
                            tags: String,
                            description: String,
                            notes: String,
                            publisher: Publisher,
                            version: String,
                            license: String,
                            website: String,
                            copyright: String,
                            dependencies: List[ManifestDependency]
                          ) extends Manifest

object ScalaManifest {
}