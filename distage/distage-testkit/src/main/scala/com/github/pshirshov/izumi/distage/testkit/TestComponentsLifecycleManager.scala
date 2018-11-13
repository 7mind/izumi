package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.ConcurrentLinkedDeque

import com.github.pshirshov.izumi.distage.roles.launcher.ComponentLifecycle
import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.LifecycleException
import com.github.pshirshov.izumi.distage.roles.roles.{ComponentsLifecycleManager, RoleComponent}
import com.github.pshirshov.izumi.distage.testkit.TestComponentsLifecycleManager._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

class TestComponentsLifecycleManager(
                                      components: Set[RoleComponent],
                                      logger: IzLogger,
                                      resourceCollection: ResourceCollection
                                    ) extends ComponentsLifecycleManager {
  private val started = new ConcurrentLinkedDeque[ComponentLifecycle]()

  override def componentsNumber: Int = components.size

  private def startComponentWithDeque(component: RoleComponent, deque: ConcurrentLinkedDeque[ComponentLifecycle]): Unit = {
    deque.push(ComponentLifecycle.Starting(component))
    component.start()
    deque.pop().discard()
    deque.push(ComponentLifecycle.Started(component))
  }

  override def startComponents(): Unit = {
    components.foreach { component =>
      component.synchronized {
        if (resourceCollection.isMemoized(component)) {
          if (!isMemoizedComponentStarted(component)) {
            logger.info(s"Starting memoized component $component...")
            startComponentWithDeque(component, memoizedComponentsLifecycle)
          } else {
            logger.info(s"Memoized component already started $component.")
          }
        } else {
          logger.info(s"Starting component $component...")
          startComponentWithDeque(component, started)
        }
      }
    }
  }

  override def stopComponents(): Set[RoleComponent] = {
    val toStop = started.asScala.toList

    logger.info(s"Going to stop ${components.size -> "count" -> null} ${toStop.niceList() -> "components"}")

    val (stopped, failed) = toStop
      .map {
        case ComponentLifecycle.Starting(c) =>
          c -> Failure(new LifecycleException(s"Component hasn't been started properly, skipping: $c"))
        case ComponentLifecycle.Started(c) =>
          c -> Try(c.stop())
      }
      .partition(_._2.isSuccess)

    logger.info(s"Service shutdown: ${stopped.size -> "stopped"} ; ${failed.size -> "failed to stop"}")

    stopped.map(_._1).toSet
  }
}

object TestComponentsLifecycleManager {
  val memoizedComponentsLifecycle = new ConcurrentLinkedDeque[ComponentLifecycle]()

  private def isMemoizedComponentStarted(component: RoleComponent) = {
    memoizedComponentsLifecycle.contains(ComponentLifecycle.Started(component)) ||
      memoizedComponentsLifecycle.contains(ComponentLifecycle.Starting(component))
  }
}