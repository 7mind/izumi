package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}

sealed trait TypeScriptModuleSchema

object TypeScriptModuleSchema {

  final case object PER_DOMAIN extends TypeScriptModuleSchema

  final case object UNITED extends TypeScriptModuleSchema

}

case class TypeScriptBuildManifest(
                                    common: Common,
                                    dependencies: List[ManifestDependency],
                                    scope: String,
                                    moduleSchema: TypeScriptModuleSchema,
                                    // this one only works with scoped namespaces, this way you can
                                    // get rid of @scope/net-company-project and use @scope/project
                                    // by using dropnameSpaceSegments = Some(2)
                                    dropNameSpaceSegments: Option[Int],
                                  ) extends BuildManifest

object TypeScriptBuildManifest {
  def default: TypeScriptBuildManifest = TypeScriptBuildManifest(
    common = BuildManifest.Common.default,
    dependencies = List(
      ManifestDependency("moment", "^2.20.1"),
      ManifestDependency("@types/node", "^10.7.1"),
      ManifestDependency("@types/websocket", "0.0.39"),
    ),
    scope = "@TestScope",
    moduleSchema = TypeScriptModuleSchema.PER_DOMAIN,
    dropNameSpaceSegments = None
  )
}
