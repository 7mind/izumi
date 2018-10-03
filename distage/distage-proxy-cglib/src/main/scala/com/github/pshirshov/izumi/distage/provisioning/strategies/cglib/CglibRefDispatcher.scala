package com.github.pshirshov.izumi.distage.provisioning.strategies.cglib

import com.github.pshirshov.izumi.distage.model.provisioning.strategies.AtomicProxyDispatcher
import net.sf.cglib.proxy.Dispatcher

// dynamic dispatching is not optimal, uhu
protected[distage] class CglibRefDispatcher(nullProxy: Any)
  extends AtomicProxyDispatcher with Dispatcher {

  override def loadObject(): AnyRef = {
    Option(reference.get())
      .getOrElse(nullProxy)
      .asInstanceOf[AnyRef]
  }
}
