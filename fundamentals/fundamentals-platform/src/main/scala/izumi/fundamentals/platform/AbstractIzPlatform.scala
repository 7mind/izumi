package izumi.fundamentals.platform

trait AbstractIzPlatform extends IzPlatformEffectfulUtil {
  def platform: ScalaPlatform

  def isHeadless: Boolean

  def terminalColorsEnabled: Boolean

  def isGraalNativeImage: Boolean
}
