package izumi.fundamentals.platform

import scala.scalajs.js

trait __AbstractIzPlatformPlatformSpecific {
  def isScalaJS: Boolean = true

  def getenvOption(s: String): Option[String] = nodeEnv.flatMap(_.get(s))

  def getRuntimeMXBeanJVMArgs(): Seq[String] = Nil

  def getClasspath(): Seq[String] = Nil

  private lazy val nodeEnv: Option[js.Dictionary[String]] = {
    val process = js.Dynamic.global.process
    if (js.isUndefined(process)) None
    else {
      val env0 = process.env
      if (js.isUndefined(env0)) None
      else {
        Some(env0.asInstanceOf[js.Dictionary[String]])
      }
    }
  }
}
