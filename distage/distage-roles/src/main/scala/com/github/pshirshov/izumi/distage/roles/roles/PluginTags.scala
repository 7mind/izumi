package com.github.pshirshov.izumi.distage.roles.roles

trait PluginTags {
  val Test = "test"
  val Dummy = "dummy"
  val Production = "production"
  val Storage = "storage"
  val ExternalApi = "external-api"
  val Common = "common"
  val Services = "services"
  val Repositories = "repositories"
  val Http = "http"
  val Implicits = "implicits"

  // TODO: combinators, https://github.com/pshirshov/izumi-r2/issues/224
//  val ProductionStorage = "storage-production"
//  val DummyStorage = "storage-dummy"
//  val DummyExternals = "external-api-dummy"
//  val ProductionExternals = "external-api-production"
}

object PluginTags extends PluginTags
