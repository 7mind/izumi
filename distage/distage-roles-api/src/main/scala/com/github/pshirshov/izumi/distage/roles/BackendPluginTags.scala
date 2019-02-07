package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.definition.BindingTag

// TODO remove
trait BackendPluginTags {
  final val Test = BindingTag("test")
  final val Dummy = BindingTag("dummy")
  final val Production = BindingTag("production")
  final val Storage = BindingTag("storage")
  final val ExternalApi = BindingTag("external-api")
  final val Common = BindingTag("common")
  final val Services = BindingTag("services")
  final val Repositories = BindingTag("repositories")
  final val Http = BindingTag("http")
  final val Implicits = BindingTag("implicits")
  final val AppOnly = BindingTag("app-only")

}

object BackendPluginTags extends BackendPluginTags
