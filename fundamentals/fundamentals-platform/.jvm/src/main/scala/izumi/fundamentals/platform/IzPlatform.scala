package izumi.fundamentals.platform

object IzPlatform extends AbstractIzPlatform {
  def platform: ScalaPlatform = if (isGraalNativeImage) {
    ScalaPlatform.GraalVMNativeImage
  } else {
    ScalaPlatform.JVM
  }

  def isHeadless: Boolean = java.awt.GraphicsEnvironment.isHeadless

  def hasColorfulTerminal: Boolean = {
    val maybeTerm = Option(System.getenv("TERM"))
    maybeTerm.isDefined
  }

  def terminalColorsEnabled: Boolean = {
    import izumi.fundamentals.platform.basics.IzBoolean._

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
