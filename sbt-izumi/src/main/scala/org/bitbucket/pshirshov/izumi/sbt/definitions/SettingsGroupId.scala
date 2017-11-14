package org.bitbucket.pshirshov.izumi.sbt.definitions

trait SettingsGroupId

object SettingsGroupId {
  def apply(): SettingsGroupId = new SettingsGroupId() {}
  
  case object GlobalSettingsGroup extends SettingsGroupId
  case object RootSettingsGroup extends SettingsGroupId
  case object ItSettingsGroup extends SettingsGroupId

}
