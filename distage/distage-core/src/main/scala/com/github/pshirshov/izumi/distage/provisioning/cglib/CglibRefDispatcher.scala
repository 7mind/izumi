package com.github.pshirshov.izumi.distage.provisioning.cglib

import java.util.concurrent.atomic.AtomicReference

import net.sf.cglib.proxy.Dispatcher




// dynamic dispatching is not optimal, uhu
protected[distage] class CglibRefDispatcher(nullProxy: AnyRef) extends Dispatcher {
  val reference = new AtomicReference[AnyRef](null)

  override def loadObject(): AnyRef = {
    Option(reference.get())
      .getOrElse(nullProxy)
  }
}
