package com.github.pshirshov.izumi.distage.testkit.services.st.adapter

import java.util.concurrent.atomic.AtomicBoolean

@deprecated("Use dstest", "2019/Jul/18")
private[testkit] trait SuppressionSupport {
  this: IgnoreSupport =>

  private val suppressAll = new AtomicBoolean(false)

  protected final def suppressTheRestOfTestSuite(): Unit = {
    suppressAll.set(true)
  }

  protected[this] final def verifyTotalSuppression(): Unit = {
    if (suppressAll.get()) {
      ignoreThisTest("The rest of this test suite has been suppressed")
    }
  }

}
