package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.roles.launcher.{ComponentLifecycle, ComponentsLifecycleManagerImpl}
import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent
import com.github.pshirshov.izumi.distage.testkit.MemoizingDistageResourceCollection.memoizedComponentsLifecycle
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger

class TestComponentsLifecycleManager(
                                      components: Set[RoleComponent],
                                      logger: IzLogger,
                                      resourceCollection: DistageResourceCollection
                                    ) extends ComponentsLifecycleManagerImpl(components.filter(!resourceCollection.isMemoized(_)), logger) {

  private val memoizedComponents = components.filter(resourceCollection.isMemoized)

  private def isMemoizedComponentStarted(component: RoleComponent): Boolean = {
    memoizedComponentsLifecycle.contains(ComponentLifecycle.Started(component)) ||
      memoizedComponentsLifecycle.contains(ComponentLifecycle.Starting(component))
  }

  override def startComponents(): Unit = {
    super.startComponents()
    memoizedComponents.foreach {
      component =>
        component.synchronized {
          if (!isMemoizedComponentStarted(component)) {
            logger.info(s"Starting memoized component $component...")
            memoizedComponentsLifecycle.push(ComponentLifecycle.Starting(component))
            component.start()
            memoizedComponentsLifecycle.pop().discard()
            memoizedComponentsLifecycle.push(ComponentLifecycle.Started(component))
          } else {
            logger.info(s"Memoized component already started $component.")
          }
        }
    }
  }
}