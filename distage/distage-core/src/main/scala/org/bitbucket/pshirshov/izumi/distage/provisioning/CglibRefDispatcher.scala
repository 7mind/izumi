package org.bitbucket.pshirshov.izumi.distage.provisioning

import java.util.concurrent.atomic.AtomicReference

import net.sf.cglib.proxy.LazyLoader
import org.bitbucket.pshirshov.izumi.distage.commons.Value
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingRefException


protected[distage] class CglibRefDispatcher(key: DIKey) extends LazyLoader {
  val reference = new AtomicReference[AnyRef](null)


  override def loadObject(): AnyRef = {
    Value(reference.get())
      .eff(checkNotNull)
      .get
  }

  // we cannot override .toString for Dispatcher/LazyLoader and MethodInterceptor doesn't work because of trait support issues
  // TODO: write custom callback policy/get rid of cglib/use double proxying specifically for .toString
  protected def checkNotNull(ref: AnyRef): Unit = {
    if (ref == null) {
      throw new MissingRefException(s"Proxy $key is not yet initialized", Set(key), None)
    }
  }

//  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
//    Option(reference.get()) match {
//      case Some(v) =>
//        methodProxy.invokeSuper(v, objects)
//      case _ if method.getName == "toString" && method.getParameterCount == 0 =>
//        s"UninitializedProxy:$key"
//      case _ =>
//        throw new MissingRefException(s"Proxy $key is not yet initialized", Set(key), None)
//    }
//  }
}
