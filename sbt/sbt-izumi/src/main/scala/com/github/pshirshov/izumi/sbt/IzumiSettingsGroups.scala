package com.github.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiSettingsGroups extends AutoPlugin {
  //noinspection TypeAnnotation
  object autoImport {
    trait SettingsGroupId {
      def name: String

      override def toString: String = s"$name:${hashCode()}"
    }

    object SettingsGroupId {
      def apply(n: String): SettingsGroupId = new SettingsGroupId() {
        override def name: String = n
      }

      case object GlobalSettingsGroup extends SettingsGroupId {
        override def name: String = "global"
      }
      case object RootSettingsGroup extends SettingsGroupId {
        override def name: String = "root"
      }
      case object ItSettingsGroup extends SettingsGroupId{
        override def name: String = "it¬"
      }

    }

  }
}
