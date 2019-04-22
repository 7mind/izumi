package com.github.pshirshov.izumi.distage.testkit.legacy

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque, ExecutorService, TimeUnit}

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.roles.RoleComponent
import com.github.pshirshov.izumi.distage.roles.launcher.ComponentLifecycle
import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.LifecycleException
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

/**
  * Dangerous sideeffectful thing. Use only in case you know what you do.
  *
  * Allows you to remember instances produced during your tests by a predicate
  * then reuse these instances in other tests within the same classloader.
  */
abstract class MemoizingDistageResourceCollection
(
  protected val instanceStore: DirtyGlobalTestResourceStore = MemoizingDistageResourceCollection
) extends DistageResourceCollection {

  instanceStore.init() // add shutdown hook only when we use memoizer

  // synchronization needed to avoid race between memoized components start
  override def startMemoizedComponents(components: Set[RoleComponent])(implicit logger: IzLogger): Unit =
    instanceStore.memoizedComponentsLifecycle.synchronized {
      components.foreach {
        component =>
          component.synchronized {
            if (!isMemoizedComponentStarted(component)) {
              logger.info(s"Starting memoized component $component...")
              instanceStore.memoizedComponentsLifecycle.push(ComponentLifecycle.Starting(component))
              component.start()
              instanceStore.memoizedComponentsLifecycle.pop().discard()
              instanceStore.memoizedComponentsLifecycle.push(ComponentLifecycle.Started(component))
            } else {
              logger.info(s"Memoized component already started $component.")
            }
          }
      }
  }

  override def isMemoized(resource: Any): Boolean = {
    instanceStore.memoizedInstances.containsValue(resource)
  }

  override def transformPlanElement(op: ExecutableOp): ExecutableOp = synchronized {
    Option(instanceStore.memoizedInstances.get(op.target)) match {
      case Some(value) =>
        ExecutableOp.WiringOp.ReferenceInstance(op.target, Wiring.SingletonWiring.Instance(op.target.tpe, value), op.origin)
      case None =>
        op
    }
  }

  override def processContext(context: Locator): Unit = {
    context.instances.foreach {
      instanceRef =>
        if (memoize(instanceRef)) {
          instanceStore.memoizedInstances.put(instanceRef.key, instanceRef.value)
        }
    }
  }

  private def isMemoizedComponentStarted(component: RoleComponent): Boolean = {
    instanceStore.memoizedComponentsLifecycle.contains(ComponentLifecycle.Started(component)) ||
      instanceStore.memoizedComponentsLifecycle.contains(ComponentLifecycle.Starting(component))
  }

  def memoize(ref: IdentifiedRef): Boolean
}

object MemoizingDistageResourceCollection extends DirtyGlobalTestResourceStoreImpl



trait DirtyGlobalTestResourceStore {

  def init(): Unit

  def memoizedInstances: ConcurrentHashMap[DIKey, Any]

  def memoizedComponentsLifecycle: ConcurrentLinkedDeque[ComponentLifecycle]

}

class DirtyGlobalTestResourceStoreImpl() extends DirtyGlobalTestResourceStore {
  /**
    * All the memoized instances available in current classloader.
    *
    * Be VERY careful when accessing this field.
    */
  override val memoizedInstances = new ConcurrentHashMap[DIKey, Any]()

  /**
    * Contains all lifecycle states of memoized [[RoleComponent]]
    */
  override val memoizedComponentsLifecycle = new ConcurrentLinkedDeque[ComponentLifecycle]()

  override def init(): Unit = {
    if (initialized.compareAndSet(false, true)) {
      val shutdownHook = new Thread(() => {
          val ignore = shutdownComponents()
          shutdownCloseables(ignore)
          shutdownExecutors()
          memoizedInstances.clear()
        }, "distage-testkit-finalizer")

      Runtime.getRuntime.addShutdownHook(shutdownHook)
    }
  }

  private val initialized = new AtomicBoolean(false)

  private def getCloseables: List[AutoCloseable] = {
    memoizedInstances.values().asScala.collect {
      case ac: AutoCloseable => ac
    }.toList.reverse
  }

  private def getExecutors: List[ExecutorService] = {
    memoizedInstances.values().asScala.collect {
      case ec: ExecutorService => ec
    }.toList.reverse
  }

  private def shutdownExecutors(): Unit = {
    val toClose = getExecutors
      .filterNot(es => es.isShutdown || es.isTerminated)

    toClose.foreach { es =>
      es.shutdown()
      if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
        es.shutdownNow()
      }
    }
  }

  private def shutdownCloseables(ignore: Set[RoleComponent]): Unit = {
    val closeables = getCloseables.filter {
      case rc: RoleComponent if ignore.contains(rc) => false
      case _ => true
    }
    closeables.foreach(_.close())
  }

  private def shutdownComponents(): Set[RoleComponent] = {
    val toStop = memoizedComponentsLifecycle.asScala.toList.reverse
    val (stopped, _) = toStop
      .map {
        case ComponentLifecycle.Starting(c) =>
          c -> Failure(new LifecycleException(s"Component hasn't been started properly, skipping: $c"))
        case ComponentLifecycle.Started(c) =>
          c -> Try(c.stop())
      }
      .partition(_._2.isSuccess)
    stopped.map(_._1).toSet
  }

}
