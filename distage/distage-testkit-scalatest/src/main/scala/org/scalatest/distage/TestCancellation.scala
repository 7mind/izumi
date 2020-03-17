package org.scalatest.distage

import org.scalatest.exceptions.TestCanceledException

object TestCancellation {
  def cancel(message: Option[String], cause: Option[Throwable], delta: Int = 0): Nothing = {
    val exception = new TestCanceledException(message, cause, failedCodeStackDepth = 1 + delta)
    val trace = exception.getStackTrace.filterNot(c => c.getClassName.startsWith("org.scalatest.") || c.getClassName.startsWith("sbt."))
    exception.setStackTrace(trace)
    throw exception
  }
}
