package com.github.pshirshov.izumi.distage.roles.roles

// FIXME remove
trait BackendPluginTags {
  final val Test = "test"
  final val Dummy = "dummy"
  final val Production = "production"
  final val Storage = "storage"
  final val ExternalApi = "external-api"
  final val Common = "common"
  final val Services = "services"
  final val Repositories = "repositories"
  final val Http = "http"
  final val Implicits = "implicits"
}

object BackendPluginTags extends BackendPluginTags
