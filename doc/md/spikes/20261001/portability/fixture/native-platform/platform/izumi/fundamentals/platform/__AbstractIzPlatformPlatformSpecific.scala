package izumi.fundamentals.platform

import scala.collection
import scala.concurrent.ExecutionContext

/**
  * Spike Native variant of the JVM file: same environment and execution-context behavior, without the
  * classpath (`URLClassLoader`) and JMX (`ManagementFactory`) introspection that Scala Native does not provide.
  */
trait __AbstractIzPlatformPlatformSpecific {
  final val isScalaJS = false

  def getenvOption(s: String): Option[String] = Option(System.getenv(s))

  def getClasspath(): Seq[String] = Nil

  def getRuntimeMXBeanJVMArgs(): collection.Seq[String] = Nil

  def platformGlobalExecutionContext: ExecutionContext = ExecutionContext.global
}
