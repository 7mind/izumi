package com.github.pshirshov.izumi.idealingua.compiler

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, License, MFUrl, ManifestDependency}
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
import com.github.pshirshov.izumi.idealingua.model.publishing.{ProjectVersion, Publisher}

trait Codecs {

  import _root_.io.circe._
  import _root_.io.circe.generic.extras.semiauto
  import _root_.io.circe.generic.semiauto._

  implicit def decMFUrl: Decoder[MFUrl] = deriveDecoder

  implicit def decLicense: Decoder[License] = deriveDecoder

  implicit def decCommon: Decoder[Common] = deriveDecoder

  implicit def decMdep: Decoder[ManifestDependency] = deriveDecoder

  implicit def decPublisher: Decoder[Publisher] = deriveDecoder

  implicit def decTsModuleSchema: Decoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationDecoder

  implicit def decScala: Decoder[ScalaBuildManifest] = deriveDecoder

  implicit def decScalaProjectLayout: Decoder[ScalaProjectLayout] = semiauto.deriveEnumerationDecoder

  implicit def decTs: Decoder[TypeScriptBuildManifest] = deriveDecoder

  implicit def decGo: Decoder[GoLangBuildManifest] = deriveDecoder

  implicit def decCs: Decoder[CSharpBuildManifest] = deriveDecoder

  implicit def encMFUrl: Encoder[MFUrl] = deriveEncoder

  implicit def encLicense: Encoder[License] = deriveEncoder

  implicit def encCommon: Encoder[Common] = deriveEncoder

  implicit def encMdep: Encoder[ManifestDependency] = deriveEncoder

  implicit def encPublisher: Encoder[Publisher] = deriveEncoder

  implicit def encTsModuleSchema: Encoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationEncoder

  implicit def encScala: Encoder[ScalaBuildManifest] = deriveEncoder

  implicit def encScalaProjectLayout: Encoder[ScalaProjectLayout] = semiauto.deriveEnumerationEncoder

  implicit def encTs: Encoder[TypeScriptBuildManifest] = deriveEncoder

  implicit def encGo: Encoder[GoLangBuildManifest] = deriveEncoder

  implicit def encCs: Encoder[CSharpBuildManifest] = deriveEncoder

  implicit def decProjectVersion: Decoder[ProjectVersion] = deriveDecoder

  implicit def encProjectVersion: Encoder[ProjectVersion] = deriveEncoder
}

object Codecs extends Codecs {

}
