package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.exceptions.{MissingRefException, ProxyAlreadyInitializedException}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait DistageProxy {
  def _distageProxyReference: AnyRef
}

trait ProxyDispatcher {
  def key: DIKey
  def init(real: Any): Unit
}

trait AtomicProxyDispatcher extends ProxyDispatcher {
  protected val reference = new AtomicReference[AnyRef](null)

  override def init(real: Any): Unit = {
    if (!reference.compareAndSet(null, real.asInstanceOf[AnyRef])) {
      throw new ProxyAlreadyInitializedException(s"Proxy $this is already initialized with value ${reference.get()} but got new value $real")
    }
  }
}

class ByNameDispatcher(val key: DIKey)
  extends Function0[Any]
    with AtomicProxyDispatcher {
  override def apply(): Any = {
    Option(reference.get()) match {
      case Some(value) =>
        value
      case None =>
        throw new MissingRefException(s"By-Name proxy $key is not yet initialized", Set(key), None)
    }
  }
}
