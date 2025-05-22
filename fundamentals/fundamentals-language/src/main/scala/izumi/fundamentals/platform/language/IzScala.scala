package izumi.fundamentals.platform.language

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

trait IzScala extends IzPlatformEffectfulUtil {
  def scalaRelease(using ev: ScalaReleaseMaterializer): ScalaRelease = ev.release
}

object IzScala extends IzScala {}
