package izumi.fundamentals.platform

import scala.collection
import scala.concurrent.ExecutionContext

trait __AbstractIzPlatformPlatformSpecific {
  final val isScalaJS = false
  final val isScalaNative = true

  def getenvOption(s: String): Option[String] = Option(System.getenv(s))

  def getClasspath(): Seq[String] = Nil

  def getRuntimeMXBeanJVMArgs(): collection.Seq[String] = Nil

  def platformGlobalExecutionContext: ExecutionContext = ExecutionContext.global
}
