package com.github.pshirshov.izumi.fundamentals.platform.time

import scala.concurrent.duration.Duration

final class IzDuration(private val duration: Duration) extends AnyVal {
  def readable: String = {
    val days = duration.toDays
    val hours = duration.toHours % 24
    val minutes = duration.toMinutes % 60
    val seconds = duration.toSeconds % 60
    s"${days}d, ${hours}h, ${minutes}m, ${seconds}s"
  }
}
