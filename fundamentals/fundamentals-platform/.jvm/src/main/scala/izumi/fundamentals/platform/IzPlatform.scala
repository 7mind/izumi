package izumi.fundamentals.platform

import izumi.fundamentals.platform.jvm.IzJvm
import izumi.fundamentals.platform.os.{IzOs, OsType}

object IzPlatform extends AbstractIzPlatform {
  def platform: ScalaPlatform = if (isGraalNativeImage) {
    ScalaPlatform.GraalVMNativeImage
  } else {
    ScalaPlatform.JVM
  }

  lazy val terminalColorsEnabled: Boolean = _terminalColorsEnabled
  lazy val isHeadless: Boolean = _isHeadless

  private def _isHeadless: Boolean = {
    import izumi.fundamentals.platform.strings.IzString.*
    val hasDisplay = Option(System.getenv("DISPLAY")).isDefined
    val hasXdgSession = Option(System.getenv("XDG_SESSION_TYPE")).isDefined
    val hasNoUIOnLinux = IzOs.osType == OsType.Linux && !hasDisplay && !hasXdgSession

    val hasAwtToolkit = Option(System.getProperty("awt.toolkit")).exists(_.nonEmpty)

    val uiDisabled = System.getProperty("java.awt.headless").asBoolean(false)
    val forcedHeadless = PlatformProperties.`izumi.app.forced-headless`.boolValue(false)

    if (uiDisabled || forcedHeadless) {
      return true
    }

    if (hasAwtToolkit) {
      return true
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
    val colorTermIsSet = Option(System.getenv("COLORTERM")).exists(_.nonEmpty)
    if (colorTermIsSet) {
      return true
    }

    val termIsSet = Option(System.getenv("TERM")).exists(_.nonEmpty)
    if (termIsSet) {
      return true
    }

    val isIdea = IzJvm.safeClasspathSeq().exists {
      s =>
        val lower = s.toLowerCase
        lower.contains("jetbrains") || lower.contains("intellijidea")
    }
    if (isIdea) {
      return true
    }

    import java.lang.management.ManagementFactory
    import scala.jdk.CollectionConverters.*
    val runtimeMXBean = ManagementFactory.getRuntimeMXBean
    val jvmArgs = runtimeMXBean.getInputArguments.asScala
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

  def isGraalNativeImage: Boolean = {
    val props = Seq(
      "org.graalvm.nativeimage.imagecode",
      "org.graalvm.nativeimage.kind",
    )
    props.exists(p => Option(System.getProperty(p)).isDefined)
  }
}
