package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}


sealed trait TypeScriptModuleSchema

object TypeScriptModuleSchema {
  case object PER_DOMAIN extends TypeScriptModuleSchema
  case object UNITED extends TypeScriptModuleSchema
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
                          ) extends BuildManifest

object TypeScriptBuildManifest {
  def generatePackage(manifest: TypeScriptBuildManifest, main: String, name: String, peerDependencies: List[ManifestDependency] = List.empty): String = {
    s"""{
       |  "name": "${if (manifest.scope.isEmpty) name else manifest.scope + "/" + name}",
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
