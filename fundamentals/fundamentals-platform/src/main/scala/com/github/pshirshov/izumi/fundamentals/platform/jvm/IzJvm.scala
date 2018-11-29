package com.github.pshirshov.izumi.fundamentals.platform.jvm

import java.lang.management.ManagementFactory
import java.net.{URLClassLoader, URLDecoder}
import java.time.ZonedDateTime

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

trait IzJvm {

  import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._

  def uptime: Duration = Duration(getUptime, scala.concurrent.duration.MILLISECONDS)

  def startTime: ZonedDateTime = getStartTime.asEpochMillisUtc

  protected def getUptime: Long = ManagementFactory.getRuntimeMXBean.getUptime

  protected def getStartTime: Long = ManagementFactory.getRuntimeMXBean.getStartTime

  @tailrec
  private def extractCp(classLoader: Option[ClassLoader], cp: Seq[String]): Seq[String] = {
    val clCp = classLoader match {
      case Some(u: URLClassLoader) =>
        u
          .getURLs
          .map(u => URLDecoder.decode(u.getFile, "UTF-8"))
          .toSeq
      case _ =>
        Seq.empty
    }

    val all = cp ++ clCp
    val parent = classLoader.flatMap(c => Option(c.getParent))
    parent match {
      case Some(cl) =>
        extractCp(Option(cl), all)
      case None =>
        all
    }
  }

  def safeClasspathSeq(classLoader: ClassLoader): Seq[String] = {
    val classLoaderCp = extractCp(Option(classLoader), Seq.empty)

    Seq(
      classLoaderCp,
      System.getProperty("java.class.path").split(':').toSeq
    ).flatten
  }

  def baseClassloader: ClassLoader = {
    Thread
      .currentThread
      .getContextClassLoader
      .getParent
  }

  def safeClasspath(classLoader: ClassLoader): String = {
    safeClasspathSeq(classLoader)
      .mkString(System.getProperty("path.separator"))
  }

  def safeClasspath(): String = safeClasspath(baseClassloader)

  def safeClasspathSeq(): Seq[String] = safeClasspathSeq(baseClassloader)
}

object IzJvm extends IzJvm {
}
