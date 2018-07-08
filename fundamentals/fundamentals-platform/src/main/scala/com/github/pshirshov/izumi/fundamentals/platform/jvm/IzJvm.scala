package com.github.pshirshov.izumi.fundamentals.platform.jvm

import java.lang.management.ManagementFactory

import scala.concurrent.duration.Duration

object IzJvm {
  def uptime: Long = Duration.apply(getUptime, scala.concurrent.duration.MILLISECONDS).toSeconds
  def startTime: Long = Duration.apply(getStartTime, scala.concurrent.duration.MILLISECONDS).toSeconds

  private def getUptime: Long = ManagementFactory.getRuntimeMXBean.getUptime
  private def getStartTime: Long = ManagementFactory.getRuntimeMXBean.getStartTime
}
