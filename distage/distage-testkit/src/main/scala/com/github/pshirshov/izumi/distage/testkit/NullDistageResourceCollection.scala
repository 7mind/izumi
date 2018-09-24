package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.JavaConverters._

object NullDistageResourceCollection extends DistageResourceCollection {
  override def transformPlanElement(op: ExecutableOp): ExecutableOp = op

  override def close(closeable: AutoCloseable): Unit = closeable.close()

  override def processContext(context: Locator): Unit = {}
}

/**
  * Dangerous sideeffectful thing. Use only in case you know what you do.
  *
  * Allows you to remember instances produced during your tests by a predicate
  * then reuse these instances in other tests within the same classloader.
  */
abstract class MemoizingDistageResourceCollection extends DistageResourceCollection {

  import MemoizingDistageResourceCollection._

  init() // this way we would start shutdown hook only in case we use memoizer

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
}

object MemoizingDistageResourceCollection {
  /**
    * All the memoized instances available in current classloader.
    *
    * Be VERY careful when accessing this field.
    */
  val memoizedInstances = new ConcurrentHashMap[DIKey, Any]()

  private val shutdownHook = new Thread(() => {
    val closeables = memoizedInstances.values().asScala.collect {
      case ac: AutoCloseable => ac
    }

    closeables.foreach(_.close())
    memoizedInstances.clear()
  }, "distage-testkit-finalizer")

  private val initialized = new AtomicBoolean(false)

  private def init(): Unit = {
    if (initialized.compareAndSet(false, true)) {
      Runtime.getRuntime.addShutdownHook(shutdownHook)
    }
  }
}
