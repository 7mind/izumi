package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.exceptions.{MissingRefException, ProxyAlreadyInitializedException}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait ProxyDispatcher {
  def init(real: Any): Unit
}

trait AtomicProxyDispatcher extends ProxyDispatcher {
  protected val reference = new AtomicReference[Any](null)

  override def init(real: Any): Unit = {
    if (!reference.compareAndSet(null, real)) {
      throw new ProxyAlreadyInitializedException(s"Proxy $this is already initialized with value ${reference.get()} but got new value $real")
    }
  }
}

class ByNameDispatcher(key: RuntimeDIUniverse.DIKey)
  extends Function0[Any] with AtomicProxyDispatcher {
  override def apply(): Any = {
    Option(reference.get()) match {
      case Some(value) =>
        value
      case None =>
        throw new MissingRefException(s"By-Name proxy $key is not yet initialized", Set(key), None)
    }
  }
}
