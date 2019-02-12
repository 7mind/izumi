package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.ConcurrentLinkedDeque

import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.LifecycleException
import com.github.pshirshov.izumi.distage.roles.{ComponentsLifecycleManager, RoleComponent}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

class ComponentsLifecycleManagerImpl(
                                      components: => Set[RoleComponent]
                                      , logger: IzLogger
                                    ) extends ComponentsLifecycleManager {

  private val started = new ConcurrentLinkedDeque[ComponentLifecycle]()

  override def componentsNumber: Int = components.size

  override def startComponents(): Unit = {
    components.foreach {
      service =>
        service.synchronized {
          logger.info(s"Starting component $service...")
          started.push(ComponentLifecycle.Starting(service))
          service.start()
          started.pop().discard()
          started.push(ComponentLifecycle.Started(service))
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
