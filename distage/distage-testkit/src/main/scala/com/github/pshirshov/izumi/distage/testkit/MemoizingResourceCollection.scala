package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.{ConcurrentHashMap, ExecutorService, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.roles.launcher.ComponentsLifecycleManager
import com.github.pshirshov.izumi.distage.roles.roles.{ResourceCollection, RoleComponent}
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.JavaConverters._


/**
  * Dangerous sideeffectful thing. Use only in case you know what you do.
  *
  * Allows you to remember instances produced during your tests by a predicate
  * then reuse these instances in other tests within the same classloader.
  */
abstract class MemoizingResourceCollection extends ResourceCollection {

  import MemoizingResourceCollection._

  init() // this way we would start shutdown hook only in case we use memoizer

  override def startMemoizedComponents(): Unit = {
    Option(MemoizingResourceCollection.memoizedComponentsLifecycleManager.get()) match {
      case None =>
        val components = getComponents
        val componentsLifecycleManager = new ComponentsLifecycleManager(components, IzLogger.NullLogger)
        MemoizingResourceCollection.memoizedComponentsLifecycleManager.set(componentsLifecycleManager)
        componentsLifecycleManager.startComponents()
      case _ => ()
    }
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

  override def close(closeable: AutoCloseable): Unit = {
    val memoized = memoizedInstances.values().asScala.toSet
    if (!memoized.contains(closeable)) {
      closeable.close()
    }
  }

  override def getCloseables: Set[AutoCloseable] = MemoizingResourceCollection.getCloseables

  override def getComponents: Set[RoleComponent] = MemoizingResourceCollection.getComponents

  override def getExecutors: Set[ExecutorService] = MemoizingResourceCollection.getExecutors
}

object MemoizingResourceCollection {
  /**
    * All the memoized instances available in current classloader.
    *
    * Be VERY careful when accessing this field.
    */
  val memoizedInstances = new ConcurrentHashMap[DIKey, Any]()

  private val memoizedComponentsLifecycleManager = new AtomicReference[ComponentsLifecycleManager](null)
  private val initialized = new AtomicBoolean(false)

  private def getCloseables: Set[AutoCloseable] = {
    memoizedInstances.values().asScala.collect {
      case ac: AutoCloseable => ac
    }.toSet
  }

  private def getComponents: Set[RoleComponent] = {
    memoizedInstances.values().asScala.collect {
      case rc: RoleComponent => rc
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
    val closeables = getCloseables
    closeables.foreach(_.close())
  }

  private def shutdownComponents(): Set[RoleComponent] = {
    Option(memoizedComponentsLifecycleManager.get()) match {
      case Some(clm) => clm.stopComponents()
      case _ => Set.empty
    }
  }

  private val shutdownHook = new Thread(() => {
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
