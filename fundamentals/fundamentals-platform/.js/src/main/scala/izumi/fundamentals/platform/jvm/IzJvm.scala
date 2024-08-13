package izumi.fundamentals.platform.jvm

import izumi.fundamentals.platform.{IzPlatform, IzPlatformEffectfulUtil}


trait IzJvm extends IzPlatformEffectfulUtil {
  @deprecated("Use IzPlatform", "28/04/2022")
  def isHeadless: Boolean = IzPlatform.isHeadless

  @deprecated("Use IzPlatform", "28/04/2022")
  def terminalColorsEnabled: Boolean = IzPlatform.terminalColorsEnabled
}

object IzJvm extends IzJvm {}
