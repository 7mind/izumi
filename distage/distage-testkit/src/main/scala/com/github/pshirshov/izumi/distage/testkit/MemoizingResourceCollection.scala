package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, TimeUnit}

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.roles.launcher.ComponentLifecycle
import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.LifecycleException
import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

/**
  * Dangerous sideeffectful thing. Use only in case you know what you do.
  *
  * Allows you to remember instances produced during your tests by a predicate
  * then reuse these instances in other tests within the same classloader.
  */
abstract class MemoizingResourceCollection extends ResourceCollection {

  import MemoizingResourceCollection._

  init() // this way we would start shutdown hook only in case we use memoizer

  override def isMemoized(resource: Any): Boolean = {
    memoizedInstances.containsValue(resource)
  }

  override def transformPlanElement(op: ExecutableOp): ExecutableOp = {
    Option(memoizedInstances.get(op.target)) match {
      case Some(value) =>
        ExecutableOp.WiringOp.ReferenceInstance(op.target, Wiring.UnaryWiring.Instance(op.target.tpe, value), op.origin)
      case None =>
        op
    }
  }

  override def processContext(context: Locator): Unit = {
    context.instances.foreach {
      instanceRef =>
        if (memoize(instanceRef)) {
          memoizedInstances.put(instanceRef.key, instanceRef.value)
        }
    }
  }

  def memoize(ref: IdentifiedRef): Boolean
}

object MemoizingResourceCollection {
  /**
    * All the memoized instances available in current classloader.
    *
    * Be VERY careful when accessing this field.
    */
  val memoizedInstances = new ConcurrentHashMap[DIKey, Any]()

  private val initialized = new AtomicBoolean(false)

  private def getCloseables: Set[AutoCloseable] = {
    memoizedInstances.values().asScala.collect {
      case ac: AutoCloseable => ac
    }.toSet
  }

  private def getExecutors: Set[ExecutorService] = {
    memoizedInstances.values().asScala.collect {
      case ec: ExecutorService => ec
    }.toSet
  }

  private def shutdownExecutors(): Unit = {
    val toClose = getExecutors.toList.reverse
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
      case rc: RoleComponent if ignore.contains(rc) =>
        false
      case _ => true
    }.toList.reverse
    closeables.foreach(_.close())
  }

  private def shutdownComponents(): Set[RoleComponent] = {
    val toStop = TestComponentsLifecycleManager.memoizedComponentsLifecycle.asScala.toList.reverse
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

  private def shutdownHook = new Thread(() => {
    val ignore = shutdownComponents()
    shutdownCloseables(ignore)
    shutdownExecutors()
    memoizedInstances.clear()
  }, "distage-testkit-finalizer")

  private def init(): Unit = {
    if (initialized.compareAndSet(false, true)) {
      Runtime.getRuntime.addShutdownHook(shutdownHook)
    }
  }
}
