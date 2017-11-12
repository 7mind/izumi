package org.bitbucket.pshirshov.izumi.sbt.definitions

trait SettingsGroupId

object SettingsGroupId {

  case object GlobalSettingsGroup extends SettingsGroupId
  case object RootSettingsGroup extends SettingsGroupId
  case object ItSettingsGroup extends SettingsGroupId

}
