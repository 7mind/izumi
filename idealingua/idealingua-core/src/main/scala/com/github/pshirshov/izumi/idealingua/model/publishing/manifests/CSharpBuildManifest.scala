package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}


case class CSharpBuildManifest(name: String,
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
                          ) extends BuildManifest

object CSharpBuildManifest {
  def default = CSharpBuildManifest(
    name = "TestBuild",
    tags = "",
    description = "Test Description",
    notes = "",
    publisher = Publisher("Test Publisher Name", "test_publisher_id"),
    version = "0.0.0",
    license = "MIT",
    website = "http://project.website",
    copyright = "Copyright (C) Test Inc.",
    dependencies = List.empty,
    "test-library",
    "",
    requireLicenseAcceptance = false,
  )

  def generateNuspec(manifest: CSharpBuildManifest, filesFolder: List[String] = List("csharp\\**")): String = {
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
