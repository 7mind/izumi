package com.github.pshirshov.izumi.distage.provisioning.cglib

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.provisioning.strategies.ProxyDispatcher
import net.sf.cglib.proxy.Dispatcher

// dynamic dispatching is not optimal, uhu
protected[distage] class CglibRefDispatcher(nullProxy: AnyRef) extends Dispatcher with ProxyDispatcher {
  private val reference = new AtomicReference[AnyRef](null)


  override def init(real: AnyRef): Unit = {
    reference.set(real)
  }

  override def loadObject(): AnyRef = {
    Option(reference.get())
      .getOrElse(nullProxy)
  }
}
