package izumi.distage.testkit.services.scalatest.dstest

import org.portablescala.reflect.{InstantiatableClass, Reflect}

import scala.annotation.unused

private[dstest] object __ClassReflectionPlatformSpecific {
  class CountDownLatch(@unused i: Int) { def await(): Unit = (); def countDown(): Unit = () }
  def clazzForName(clsName: String): InstantiatableClass = Reflect.lookupInstantiatableClass(clsName).get
  def subclassOf(clsA: InstantiatableClass, clsB: Class[?]): Boolean = clsB.isAssignableFrom(clsA.runtimeClass)
  def newInstance(clazz: InstantiatableClass): Any = clazz.newInstance()
}
