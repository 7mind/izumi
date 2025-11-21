package izumi.fundamentals.platform

import izumi.fundamentals.platform.os.{IzOs, OsType}

object IzPlatform extends AbstractIzPlatform with __AbstractIzPlatformPlatformSpecific {
  override def platform: ScalaPlatform = if (isScalaJS) {
    ScalaPlatform.Js
  } else if (isGraalNativeImage) {
    ScalaPlatform.GraalVMNativeImage
  } else {
    ScalaPlatform.JVM
  }

  override def isGraalNativeImage: Boolean = !isScalaJS && {
    val props = Seq(
      "org.graalvm.nativeimage.imagecode",
      "org.graalvm.nativeimage.kind",
    )
    props.exists(p => Option(System.getProperty(p)).isDefined)
  }

  override lazy val terminalColorsEnabled: Boolean = _terminalColorsEnabled
  override lazy val isHeadless: Boolean = _isHeadless

  private def _isHeadless: Boolean = {
    import izumi.fundamentals.platform.strings.IzString.*
    val hasDisplay = getenvOption("DISPLAY").isDefined
    val hasXdgSession = getenvOption("XDG_SESSION_TYPE").isDefined
    val hasNoUIOnLinux = IzOs.osType == OsType.Linux && !hasDisplay && !hasXdgSession

    val hasAwtToolkit = Option(System.getProperty("awt.toolkit")).exists(_.nonEmpty)

    val uiDisabled = System.getProperty("java.awt.headless").asBoolean(false)
    val forcedHeadless = PlatformProperties.`izumi.app.forced-headless`.boolValue(false)

    if (uiDisabled || forcedHeadless) {
      return true
    }

    if (hasAwtToolkit) {
      return false
    }

    hasNoUIOnLinux
  }

  private def _terminalColorsEnabled: Boolean = {

    val colorsDisabledByProperty = PlatformProperties.`izumi.app.disable-terminal-colors`.boolValue(false)
    if (colorsDisabledByProperty) {
      return false
    }

    val colorsForcedByProperty = PlatformProperties.`izumi.app.force-terminal-colors`.boolValue(false)
    if (colorsForcedByProperty) {
      return true
    }

    if (isHeadless) {
      return false
    }

    // http://jdebp.uk/Softwares/nosh/guide/TerminalCapabilities.html
    val colorTermIsSet = getenvOption("COLORTERM").exists(_.nonEmpty)
    if (colorTermIsSet) {
      return true
    }

    val termIsSet = getenvOption("TERM").exists(_.nonEmpty)
    if (termIsSet) {
      return true
    }

    val isIdea = getClasspath().exists {
      s =>
        val lower = s.toLowerCase
        lower.contains("jetbrains") || lower.contains("intellijidea")
    }
    if (isIdea) {
      return true
    }

    val jvmArgs = getRuntimeMXBeanJVMArgs()
    val hasIdeaAgent = jvmArgs.exists {
      s =>
        val lower = s.toLowerCase
        lower.contains("idea_rt.jar")
    }
    if (hasIdeaAgent) {
      return true
    }

    false
  }
}
