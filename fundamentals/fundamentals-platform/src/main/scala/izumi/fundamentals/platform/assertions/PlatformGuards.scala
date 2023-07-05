package izumi.fundamentals.platform.assertions

import izumi.fundamentals.platform.language.IzScala

trait PlatformGuards {
  final def brokenOnScala3(f: => Any): Unit = {
    if (IzScala.scalaRelease.major == 3) broken(f) else f;
    ()
  }

  final def brokenOnScala2(f: => Any): Unit = {
    if (IzScala.scalaRelease.major == 2) broken(f) else f;
    ()
  }

  def broken(f: => Any): Unit
}
