package izumi.idealingua.compiler

import izumi.idealingua.model.publishing.BuildManifest.{Common, License, MFUrl, ManifestDependency}
import izumi.idealingua.model.publishing.manifests._
import izumi.idealingua.model.publishing.{ProjectNamingRule, ProjectVersion, Publisher}

trait Codecs {

  import _root_.io.circe._
  import _root_.io.circe.generic.extras.semiauto
  import _root_.io.circe.generic.semiauto._

  implicit def decMFUrl: Decoder[MFUrl] = deriveDecoder

  implicit def decLicense: Decoder[License] = deriveDecoder

  implicit def decCommon: Decoder[Common] = deriveDecoder

  implicit def decMdep: Decoder[ManifestDependency] = deriveDecoder

  implicit def decPublisher: Decoder[Publisher] = deriveDecoder

  implicit def decProjectNamingRule: Decoder[ProjectNamingRule] = deriveDecoder


  //
  implicit def decScalaProjectLayout: Decoder[ScalaProjectLayout] = semiauto.deriveEnumerationDecoder
  
  implicit def decSbtOptions: Decoder[SbtOptions] = deriveDecoder

  implicit def decScalaBuildManifest: Decoder[ScalaBuildManifest] = deriveDecoder

  implicit def decTs: Decoder[TypeScriptBuildManifest] = deriveDecoder

  implicit def decTypeScriptProjectLayout: Decoder[TypeScriptProjectLayout] = semiauto.deriveEnumerationDecoder
  
  implicit def decYarnOptions: Decoder[YarnOptions] = deriveDecoder
  
  implicit def decGo: Decoder[GoLangBuildManifest] = deriveDecoder
  
  implicit def decGoProjectLayout: Decoder[GoProjectLayout] = semiauto.deriveEnumerationDecoder
  
  implicit def decGoRepositoryOptions: Decoder[GoRepositoryOptions] = deriveDecoder  
  
  implicit def decCs: Decoder[CSharpBuildManifest] = deriveDecoder

  implicit def decCSharpProjectLayout: Decoder[CSharpProjectLayout] = semiauto.deriveEnumerationDecoder

  implicit def decNugetOptions: Decoder[NugetOptions] = deriveDecoder  
  //
  
  implicit def encMFUrl: Encoder[MFUrl] = deriveEncoder

  implicit def encLicense: Encoder[License] = deriveEncoder

  implicit def encCommon: Encoder[Common] = deriveEncoder

  implicit def encMdep: Encoder[ManifestDependency] = deriveEncoder

  implicit def encPublisher: Encoder[Publisher] = deriveEncoder

  implicit def encProjectNamingRule: Encoder[ProjectNamingRule] = deriveEncoder


  //
  implicit def encScalaProjectLayout: Encoder[ScalaProjectLayout] = semiauto.deriveEnumerationEncoder

  implicit def encSbtOptions: Encoder[SbtOptions] = deriveEncoder

  implicit def encScalaBuildManifest: Encoder[ScalaBuildManifest] = deriveEncoder

  implicit def encTs: Encoder[TypeScriptBuildManifest] = deriveEncoder

  implicit def encTypeScriptProjectLayout: Encoder[TypeScriptProjectLayout] = semiauto.deriveEnumerationEncoder

  implicit def encYarnOptions: Encoder[YarnOptions] = deriveEncoder

  implicit def encGo: Encoder[GoLangBuildManifest] = deriveEncoder

  implicit def encGoProjectLayout: Encoder[GoProjectLayout] = semiauto.deriveEnumerationEncoder

  implicit def encGoRepositoryOptions: Encoder[GoRepositoryOptions] = deriveEncoder

  implicit def encCs: Encoder[CSharpBuildManifest] = deriveEncoder

  implicit def encCSharpProjectLayout: Encoder[CSharpProjectLayout] = semiauto.deriveEnumerationEncoder

  implicit def encNugetOptions: Encoder[NugetOptions] = deriveEncoder
  //

  implicit def decProjectVersion: Decoder[ProjectVersion] = deriveDecoder

  implicit def encProjectVersion: Encoder[ProjectVersion] = deriveEncoder

  implicit def decVersionOverlay: Decoder[VersionOverlay] = deriveDecoder

  implicit def encVersionOverlay: Encoder[VersionOverlay] = deriveEncoder
}

object Codecs extends Codecs {

}
