package com.github.pshirshov.izumi.idealingua.compiler

import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
import com.github.pshirshov.izumi.idealingua.model.publishing.{ManifestDependency, Publisher}

trait Codecs {

  import _root_.io.circe._
  import _root_.io.circe.generic.extras.semiauto
  import _root_.io.circe.generic.semiauto._

  implicit def decMdep: Decoder[ManifestDependency] = deriveDecoder

  implicit def decPublisher: Decoder[Publisher] = deriveDecoder

  implicit def decTsModuleSchema: Decoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationDecoder

  implicit def decScala: Decoder[ScalaBuildManifest] = deriveDecoder

  implicit def decTs: Decoder[TypeScriptBuildManifest] = deriveDecoder

  implicit def decGo: Decoder[GoLangBuildManifest] = deriveDecoder

  implicit def decCs: Decoder[CSharpBuildManifest] = deriveDecoder


  implicit def encMdep: Encoder[ManifestDependency] = deriveEncoder

  implicit def encPublisher: Encoder[Publisher] = deriveEncoder

  implicit def encTsModuleSchema: Encoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationEncoder

  implicit def encScala: Encoder[ScalaBuildManifest] = deriveEncoder

  implicit def encTs: Encoder[TypeScriptBuildManifest] = deriveEncoder

  implicit def encGo: Encoder[GoLangBuildManifest] = deriveEncoder

  implicit def encCs: Encoder[CSharpBuildManifest] = deriveEncoder
}
