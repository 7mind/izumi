package izumi.fundamentals.platform

import izumi.fundamentals.platform.jvm.IzJvm

import scala.collection

trait __AbstractIzPlatformPlatformSpecific {
  def isScalaJS: Boolean = false

  def getenvOption(s: String): Option[String] = Option(System.getenv(s))

  def getClasspath(): Seq[String] = IzJvm.safeClasspathSeq()

  def getRuntimeMXBeanJVMArgs(): collection.Seq[String] = {
    import java.lang.management.ManagementFactory
    import scala.jdk.CollectionConverters.*

    val runtimeMXBean = ManagementFactory.getRuntimeMXBean
    val jvmArgs = runtimeMXBean.getInputArguments.asScala
    jvmArgs
  }
}
