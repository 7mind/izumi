import sbt.settingKey

object DocKeys {
  lazy val prefix = settingKey[String => String]("")
}
