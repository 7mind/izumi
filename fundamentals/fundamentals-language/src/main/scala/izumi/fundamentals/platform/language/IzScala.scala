package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

trait IzScala extends IzPlatformEffectfulUtil {
  def scalaRelease: ScalaRelease = ScalaReleaseProvider.scalaRelease
}

object IzScala extends IzScala {}
