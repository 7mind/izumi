package com.github.pshirshov.izumi.idealingua.model.publishing

final case class ManifestDependency(module: String, version: String)

object ManifestDependency {
  def ManifestDependency(module: String, version: String) = new ManifestDependency(module, version)
}

trait BuildManifest {
  def name: String
  def tags: String
  def description: String
  def notes: String
  def publisher: Publisher
  def version: String
  def license: String
  def website: String
  def copyright: String
  def dependencies: List[ManifestDependency]
}
