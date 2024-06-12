package izumi.fundamentals.platform.build

import java.time.LocalDateTime

object BuildAttributes {

  inline def javaVendorUrl(): Option[String] = ${ BuildAttributesImpl.getProp("java.vendor.url") }
  inline def javaVmVendor(): Option[String] = ${ BuildAttributesImpl.getProp("java.vm.vendor") }
  inline def javaVersion(): Option[String] = ${ BuildAttributesImpl.getProp("java.version") }
  inline def javaHome(): Option[String] = ${ BuildAttributesImpl.getProp("java.home") }
  inline def javaSpecificationVersion(): Option[String] = ${ BuildAttributesImpl.getProp("java.specification.version") }
  inline def javaVmSpecificationVersion(): Option[String] = ${ BuildAttributesImpl.getProp("java.vm.specification.version") }

  inline def osName(): Option[String] = ${ BuildAttributesImpl.getProp("os.name") }
  inline def osVersion(): Option[String] = ${ BuildAttributesImpl.getProp("os.version") }

  inline def scalaHome(): Option[String] = ${ BuildAttributesImpl.getProp("scala.home") }

  inline def userHome(): Option[String] = ${ BuildAttributesImpl.getProp("user.home") }
  inline def userName(): Option[String] = ${ BuildAttributesImpl.getProp("user.name") }
  inline def userDir(): Option[String] = ${ BuildAttributesImpl.getProp("user.dir") }

  inline def buildTimestamp(): LocalDateTime = ${ BuildAttributesImpl.buildTimestampMacro() }

  inline def sbtProjectRoot(): Option[String] = ${ BuildAttributesImpl.sbtProjectRoot() }

  inline def buildTimeProperty(inline name: String): Option[String] = ${ BuildAttributesImpl.getExprProp('{ name }) }

}
