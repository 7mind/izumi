package com.github.pshirshov.izumi.fundamentals.platform.jvm

import java.lang.management.ManagementFactory
import java.time.ZonedDateTime

import scala.concurrent.duration.Duration

trait IzJvm {

  import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._

  def uptime: Duration = Duration(getUptime, scala.concurrent.duration.MILLISECONDS)

  def startTime: ZonedDateTime = getStartTime.asEpochMillisUtc

  protected def getUptime: Long = ManagementFactory.getRuntimeMXBean.getUptime

  protected def getStartTime: Long = ManagementFactory.getRuntimeMXBean.getStartTime

}

object IzJvm extends IzJvm {
}
