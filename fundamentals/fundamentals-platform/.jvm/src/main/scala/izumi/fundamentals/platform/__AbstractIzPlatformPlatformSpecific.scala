package izumi.fundamentals.platform

import izumi.fundamentals.platform.jvm.IzJvm

import scala.collection
import scala.concurrent.ExecutionContext

trait __AbstractIzPlatformPlatformSpecific {
  final val isScalaJS = false

  def getenvOption(s: String): Option[String] = Option(System.getenv(s))

  def getClasspath(): Seq[String] = IzJvm.safeClasspathSeq()

  def getRuntimeMXBeanJVMArgs(): collection.Seq[String] = {
    import java.lang.management.ManagementFactory
    import scala.jdk.CollectionConverters.*

    val runtimeMXBean = ManagementFactory.getRuntimeMXBean
    val jvmArgs = runtimeMXBean.getInputArguments.asScala
    jvmArgs
  }

  /** [[org.scalajs.macrotaskexecutor.MacrotaskExecutor]] on Scala.js, ExecutionContext.global otherwise */
  def platformGlobalExecutionContext: ExecutionContext = ExecutionContext.global
}
