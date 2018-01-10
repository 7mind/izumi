package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.{ReadyPlan, ExecutableOp}

trait WithSanityChecks {
  protected def assertSanity(plan: ReadyPlan): Unit = {
    assertSanity(plan.getPlan)
    // TODO: make sure circular deps are gone
  }

  protected def assertKeysSanity(keys: Seq[DIKey]): Unit = {
    if (duplicates(keys).nonEmpty) {
      throw new IllegalArgumentException(s"Duplicate keys: $keys!")
    }
  }

  protected def assertSanity(ops: Seq[ExecutableOp]): Unit = {
    assertKeysSanity(ops.map(_.target))

  }

  // TODO: quadratic
  private def duplicates(keys: Seq[DIKey]): Seq[DIKey] = keys.map {
    k => (k, keys.count(_ == k))
  }.filter(_._2 > 1).map(_._1)

}
