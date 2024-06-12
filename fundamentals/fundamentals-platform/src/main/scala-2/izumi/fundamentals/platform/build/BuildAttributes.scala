package izumi.fundamentals.platform.build

import java.time.LocalDateTime

import scala.language.experimental.macros

object BuildAttributes {

  def javaVendorUrl(): Option[String] = macro BuildAttributeMacroImpl.javaVendorUrl
  def javaVmVendor(): Option[String] = macro BuildAttributeMacroImpl.javaVmVendor
  def javaVersion(): Option[String] = macro BuildAttributeMacroImpl.javaVersion
  def javaHome(): Option[String] = macro BuildAttributeMacroImpl.javaHome
  def javaSpecificationVersion(): Option[String] = macro BuildAttributeMacroImpl.javaSpecificationVersion
  def javaVmSpecificationVersion(): Option[String] = macro BuildAttributeMacroImpl.javaVmSpecificationVersion
  def osName(): Option[String] = macro BuildAttributeMacroImpl.osName
  def osVersion(): Option[String] = macro BuildAttributeMacroImpl.osVersion
  def scalaHome(): Option[String] = macro BuildAttributeMacroImpl.scalaHome
  def userHome(): Option[String] = macro BuildAttributeMacroImpl.userHome
  def userName(): Option[String] = macro BuildAttributeMacroImpl.userName
  def userDir(): Option[String] = macro BuildAttributeMacroImpl.userDir

  def buildTimestamp(): LocalDateTime = macro BuildAttributeMacroImpl.buildTimestampMacro

  def sbtProjectRoot(): Option[String] = macro BuildAttributeMacroImpl.sbtProjectRoot

  def buildTimeProperty(name: String): Option[String] = macro BuildAttributeMacroImpl.buildProperty

}
