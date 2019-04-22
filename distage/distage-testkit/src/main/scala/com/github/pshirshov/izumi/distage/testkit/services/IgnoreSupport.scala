package com.github.pshirshov.izumi.distage.testkit.services

import java.util.concurrent.atomic.AtomicBoolean

import org.scalatest.exceptions.TestCanceledException

private[testkit] trait IgnoreSupport {
  protected final def ignoreThisTest(cause: Throwable): Nothing = {
    ignoreThisTest(None, Some(cause))
  }

  protected final def ignoreThisTest(message: String): Nothing = {
    ignoreThisTest(Some(message), None)
  }

  protected final def ignoreThisTest(message: String, cause: Throwable): Nothing = {
    ignoreThisTest(Some(message), Some(cause))
  }

  protected final def ignoreThisTest(message: Option[String] = None, cause: Option[Throwable] = None): Nothing = {
    throw new TestCanceledException(message, cause, failedCodeStackDepth = 0)
  }


}


