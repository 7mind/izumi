package com.github.pshirshov.izumi.fundamentals.platform.jvm

import java.lang.management.ManagementFactory
import java.net.URLClassLoader
import java.time.ZonedDateTime

import scala.concurrent.duration.Duration

trait IzJvm {

  import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._

  def uptime: Duration = Duration(getUptime, scala.concurrent.duration.MILLISECONDS)

  def startTime: ZonedDateTime = getStartTime.asEpochMillisUtc

  protected def getUptime: Long = ManagementFactory.getRuntimeMXBean.getUptime

  protected def getStartTime: Long = ManagementFactory.getRuntimeMXBean.getStartTime

  def safeClasspathSeq(classLoader: ClassLoader): Seq[String] = {
    val classLoaderCp = classLoader match {
      case u: URLClassLoader =>
        u
          .getURLs
          .map(_.getFile)
          .toSeq
      case _ =>
        Seq.empty
    }

    Seq(
      classLoaderCp,
      Seq(System.getProperty("java.class.path"))
    ).flatten
  }

  def safeClasspath(classLoader: ClassLoader): String = {
    safeClasspathSeq(classLoader)
      .mkString(System.getProperty("path.separator"))
  }

}

object IzJvm extends IzJvm {
}
