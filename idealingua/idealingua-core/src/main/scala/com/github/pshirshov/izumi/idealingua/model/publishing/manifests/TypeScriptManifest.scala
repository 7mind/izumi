package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{Manifest, ManifestDependency, Publisher}
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.TypeScriptModuleSchema.TypeScriptModuleSchema

object TypeScriptModuleSchema extends Enumeration {
  type TypeScriptModuleSchema = Value
  val PER_DOMAIN, UNITED = Value
}

case class TypeScriptManifest (
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
                          ) extends Manifest

object TypeScriptManifest {
  def generatePackage(manifest: TypeScriptManifest, main: String, name: String, peerDependencies: List[ManifestDependency] = List.empty): String = {
    s"""{
       |  "name": "${manifest.scope}/$name",
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