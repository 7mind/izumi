package izumi.distage.testkit.services.scalatest.dstest

private[dstest] object __ClassReflectionPlatformSpecific {
  type CountDownLatch = java.util.concurrent.CountDownLatch
  def clazzForName(clsName: String): Class[?] = Class.forName(clsName)
  def subclassOf(clsA: Class[?], clsB: Class[?]): Boolean = clsB.isAssignableFrom(clsA)
  def newInstance(clazz: Class[?]): Any = clazz.getDeclaredConstructor().newInstance()
}
