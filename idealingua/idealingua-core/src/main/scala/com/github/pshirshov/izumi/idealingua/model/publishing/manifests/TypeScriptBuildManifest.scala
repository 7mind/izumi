package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}

sealed trait TypeScriptModuleSchema

object TypeScriptModuleSchema {
  final case object PER_DOMAIN extends TypeScriptModuleSchema
  final case object UNITED extends TypeScriptModuleSchema
}

case class TypeScriptBuildManifest(
                            name: String,
                            tags: String,
                            description: String,
                            notes: String,
                            publisher: Publisher,
                            version: String,
                            license: String,
                            website: String,
                            copyright: String,
                            dependencies: List[ManifestDependency],
                            scope: String,
                            moduleSchema: TypeScriptModuleSchema,
                            // this one only works with scoped namespaces, this way you can
                            // get rid of @scope/net-company-project and use @scope/project
                            // by using dropnameSpaceSegments = Some(2)
                            dropNameSpaceSegments: Option[Int],
                          ) extends BuildManifest

object TypeScriptBuildManifest {
  def generatePackage(manifest: TypeScriptBuildManifest, main: String, name: String, peerDependencies: List[ManifestDependency] = List.empty): String = {
    s"""{
       |  "name": "$name",
       |  "version": "${manifest.version}",
       |  "description": "${manifest.description}",
       |  "main": "$main.js",
       |  "typings": "$main.d.ts",
       |  "author": "${manifest.publisher.name} (${manifest.publisher.id})",
       |  "license": "${manifest.license}",
       |  "dependencies": {
       |${manifest.dependencies.map(md => s"""    "${md.module}": "${md.version}"""").mkString(",\n    ")}
       |  },
       |  "peerDependencies": {
       |${peerDependencies.map(pd => s"""    "${pd.module}": "${pd.version}"""").mkString(",\n    ")}
       |  }
       |}
     """.stripMargin
  }
}
