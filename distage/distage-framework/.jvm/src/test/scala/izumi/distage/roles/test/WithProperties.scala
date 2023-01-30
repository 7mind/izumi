package izumi.distage.roles.test

import com.typesafe.config.ConfigFactory

trait WithProperties {
  final def withProperties(properties: Map[String, String])(f: => Unit): Unit = {
    withProperties(properties.toSeq: _*)(f)
  }

  final def withProperties(properties: (String, String)*)(f: => Unit): Unit = synchronized {
    try {
      properties.foreach {
        case (k, v) =>
          System.setProperty(k, v)
      }
      ConfigFactory.invalidateCaches()
      f
    } finally {
      properties.foreach {
        case (k, _) =>
          System.clearProperty(k)
      }
      ConfigFactory.invalidateCaches()
    }
  }

}
