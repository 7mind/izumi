package izumi.fundamentals.platform.jvm

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

import java.io.File
import java.net.URLClassLoader
import scala.annotation.tailrec

trait IzClasspath extends IzPlatformEffectfulUtil {
  def safeClasspathSeq(classLoader: ClassLoader): Seq[String] = {
    val classLoaderCp = extractCp(Option(classLoader), Seq.empty)

    Seq(
      classLoaderCp,
      System.getProperty("java.class.path").split(File.pathSeparatorChar).toSeq,
    ).flatten
  }

  def safeClasspath(classLoader: ClassLoader): String = {
    safeClasspathSeq(classLoader)
      .mkString(System.getProperty("path.separator"))
  }

  def safeClasspath(): String = safeClasspath(baseClassloader)

  def safeClasspathSeq(): Seq[String] = safeClasspathSeq(baseClassloader)

  def baseClassloader: ClassLoader = {
    Thread.currentThread.getContextClassLoader.getParent
  }

  @tailrec
  private def extractCp(classLoader: Option[ClassLoader], cp: Seq[String]): Seq[String] = {
    val clCp = classLoader match {
      case Some(u: URLClassLoader) =>
        u.getURLs
          .map(u => new File(u.toURI).getPath)
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
}

object IzClasspath extends IzClasspath
