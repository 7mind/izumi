package izumi.fundamentals.platform.jvm

import izumi.fundamentals.platform.IzPlatform

import java.lang.management.ManagementFactory
import java.net.{URLClassLoader, URLDecoder}
import java.nio.file.{Path, Paths}
import java.time.ZonedDateTime
import scala.annotation.tailrec
import scala.concurrent.duration.Duration

trait IzJvm {

  import izumi.fundamentals.platform.time.IzTime._

  @deprecated("Use IzPlatform", "28/04/2022")
  def isHeadless: Boolean = IzPlatform.isHeadless

  @deprecated("Use IzPlatform", "28/04/2022")
  def hasColorfulTerminal: Boolean = IzPlatform.hasColorfulTerminal

  @deprecated("Use IzPlatform", "28/04/2022")
  def terminalColorsEnabled: Boolean = IzPlatform.terminalColorsEnabled

  def uptime: Duration = Duration(getUptime, scala.concurrent.duration.MILLISECONDS)

  def startTime: ZonedDateTime = getStartTime.asEpochMillisUtc

  def tempDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))

  protected def getUptime: Long = ManagementFactory.getRuntimeMXBean.getUptime

  protected def getStartTime: Long = ManagementFactory.getRuntimeMXBean.getStartTime

}

object IzJvm extends IzJvm with IzClasspath
