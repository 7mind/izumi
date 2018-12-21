package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.roles.RoleComponent
import com.github.pshirshov.izumi.distage.roles.launcher.ComponentsLifecycleManagerImpl
import com.github.pshirshov.izumi.logstage.api.IzLogger

class TestComponentsLifecycleManager(
                                      components: Set[RoleComponent],
                                      implicit val logger: IzLogger,
                                      resourceCollection: DistageResourceCollection
                                    ) extends ComponentsLifecycleManagerImpl(components.filter(!resourceCollection.isMemoized(_)), logger) {

  private val memoizedComponents = components.filter(resourceCollection.isMemoized)

  override def startComponents(): Unit = {
    super.startComponents()
    resourceCollection.startMemoizedComponents(memoizedComponents)
  }
}
