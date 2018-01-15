package org.bitbucket.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiSettingsGroups extends AutoPlugin {
  //noinspection TypeAnnotation
  object autoImport {
    trait SettingsGroupId

    object SettingsGroupId {
      def apply(): SettingsGroupId = new SettingsGroupId() {}

      case object GlobalSettingsGroup extends SettingsGroupId
      case object RootSettingsGroup extends SettingsGroupId
      case object ItSettingsGroup extends SettingsGroupId

    }

  }
}
