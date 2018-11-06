package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.LifecycleException
import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable
import scala.util.{Failure, Try}

class ComponentsLifecycleManager(
                                  components: Set[RoleComponent],
                                  logger: IzLogger,

                                ) {
  private val started = new mutable.ArrayStack[ComponentLifecycle]()

  def startComponents(): Unit = {
    components.foreach {
      service =>
        logger.info(s"Starting component $service...")
        started.push(ComponentLifecycle.Starting(service))
        service.start()
        started.pop().discard()
        started.push(ComponentLifecycle.Started(service))
    }
  }

  def stopComponents(): Set[RoleComponent] = {
    val toStop = started.toList
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
