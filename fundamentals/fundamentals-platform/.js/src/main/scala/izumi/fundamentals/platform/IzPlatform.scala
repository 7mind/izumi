package izumi.fundamentals.platform

object IzPlatform extends AbstractIzPlatform {
  def platform: ScalaPlatform = ScalaPlatform.Js

  def isHeadless: Boolean = false

  def hasColorfulTerminal: Boolean = false

  def terminalColorsEnabled: Boolean = false

  def isGraalNativeImage: Boolean = false
}
