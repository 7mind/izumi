package izumi.fundamentals.platform

import org.scalajs.macrotaskexecutor.MacrotaskExecutor

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.Dictionary

trait __AbstractIzPlatformPlatformSpecific {
  final val isScalaJS = true

  def getenvOption(s: String): Option[String] = nodeEnv.flatMap(_.get(s))

  def getRuntimeMXBeanJVMArgs(): Seq[String] = Nil

  def getClasspath(): Seq[String] = Nil

  def platformGlobalExecutionContext: ExecutionContext = MacrotaskExecutor

  private lazy val nodeEnv: Option[js.Dictionary[String]] = {
    for {
      process <-
        try {
          js.defined(js.Dynamic.global.process).toOption
        } catch {
          case t: java.lang.Error if t.getMessage.contains("JVM") && t.getMessage.contains("Scala.js") =>
            // We're running Sjs binaries on JVM (in a macro), env is not available
            None
        }
      env <- js.defined(process.env).toOption
    } yield env.asInstanceOf[Dictionary[String]]
  }
}
