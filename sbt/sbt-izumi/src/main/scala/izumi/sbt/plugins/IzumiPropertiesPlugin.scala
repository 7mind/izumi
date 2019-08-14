package izumi.sbt.plugins

import sbt.AutoPlugin

import scala.sys.SystemProperties
import scala.util.Try

object IzumiPropertiesPlugin extends AutoPlugin {
  //noinspection TypeAnnotation
  object autoImport {
    implicit class StringExtensions(s: String) {
      def asBoolean(default: Boolean): Boolean = {
        Try(s.toBoolean).getOrElse(default)
      }

      def asInt(default: Int): Int = {
        Try(s.toInt).getOrElse(default)
      }
    }

    implicit class PropsExtensions(props: SystemProperties) {
      def getBoolean(name: String, default: Boolean): Boolean = {
        Try(props.get(name).map(_.toBoolean)).toOption.flatten.getOrElse(default)
      }

      def getInt(name: String, default: Int): Int= {
        Try(props.get(name).map(_.toInt)).toOption.flatten.getOrElse(default)
      }
    }
  }
}
