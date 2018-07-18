package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{Manifest, ManifestDependency, Publisher}


case class CSharpManifest ( name: String,
                            tags: String,
                            description: String,
                            notes: String,
                            publisher: Publisher,
                            version: String,
                            license: String,
                            website: String,
                            copyright: String,
                            dependencies: List[ManifestDependency],
                            id: String,
                            iconUrl: String,
                            requireLicenseAcceptance: Boolean
                          ) extends Manifest

object CSharpManifest {
  def generateNuspec(manifest: CSharpManifest, filesFolder: List[String] = List("csharp\\**")): String = {
    s"""<?xml version="1.0"?>
       |<package >
       |    <metadata>
       |        <id>${manifest.id}</id>
       |        <version>${manifest.version}</version>
       |        <authors>${manifest.publisher.name}</authors>
       |        <owners>${manifest.publisher.id}</owners>
       |        <licenseUrl>${manifest.license}</licenseUrl>
       |        <projectUrl>${manifest.website}</projectUrl>
       |        <iconUrl>${manifest.iconUrl}</iconUrl>
       |        <requireLicenseAcceptance>${if(manifest.requireLicenseAcceptance)"true" else "false"}</requireLicenseAcceptance>
       |        <releaseNotes>${manifest.notes}</releaseNotes>
       |        <description>${manifest.description}</description>
       |        <copyright>${manifest.copyright}</copyright>
       |        <tags>${manifest.tags}</tags>
       |        <dependencies>
       |        </dependencies>
       |     </metadata>
       |    <files>
       |${filesFolder.map(ff => s"""        <file src="$ff" target="build" />""")}
       |    </files>
       |</package>
     """.stripMargin
  }
}