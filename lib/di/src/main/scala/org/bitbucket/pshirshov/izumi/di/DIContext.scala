package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait DIContext {
  def instance[T: Tag](key: DIKey): T

  def subInjector(): Planner
}
