package izumi.fundamentals.platform

import izumi.fundamentals.platform.basics.IzBoolean

object IzPlatform extends AbstractIzPlatform {
  def platform: ScalaPlatform = if (isGraalNativeImage) {
    ScalaPlatform.GraalVMNativeImage
  } else {
    ScalaPlatform.JVM
  }

  def isHeadless: Boolean = {
    import izumi.fundamentals.platform.strings.IzString.*
    val maybeDisplay = Option(System.getenv("DISPLAY"))
    val maybeXdgSession = Option(System.getenv("XDG_SESSION_TYPE"))
    val maybeHasAwtToolkit = Option(System.getProperty("awt.toolkit")).exists(_.nonEmpty)

    val hasNoUI = !IzBoolean.any(
      maybeDisplay.isDefined,
      maybeXdgSession.isDefined,
      maybeHasAwtToolkit,
    )

    val uiDisabled = System.getProperty("java.awt.headless").asBoolean(false)

    IzBoolean.any(
      hasNoUI,
      uiDisabled,
    )
  }

  def terminalColorsEnabled: Boolean = {
    import izumi.fundamentals.platform.basics.IzBoolean.*

    all(
      !isHeadless
      // hasColorfulTerminal, // idea doesn't set TERM :(
    )
  }

  def isGraalNativeImage: Boolean = {
    val props = Seq(
      "org.graalvm.nativeimage.imagecode",
      "org.graalvm.nativeimage.kind",
    )
    props.exists(p => Option(System.getProperty(p)).isDefined)
  }
}
