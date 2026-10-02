package izumi.fixtures.protocol

import java.io.File
import java.net.URLClassLoader

object ProtocolClassloaderConsumer {
  def main(classpath: Array[String]): Unit = {
    val urls = classpath.map(path => new File(path).toURI.toURL)
    val first = new URLClassLoader(urls, null)
    val second = new URLClassLoader(urls, null)
    val golden = "{\"schemaVersion\":1,\"message\":{\"kind\":\"cancel\",\"run\":\"classloader-consumer\"}}"
    def exchange(loader: ClassLoader, frame: String): String = {
      val moduleClass = loader.loadClass("izumi.fixtures.protocol.PublishedProtocolConsumer$")
      if (moduleClass.getClassLoader ne loader) throw new IllegalStateException("Protocol consumer escaped its isolated classloader")
      val module = moduleClass.getField("MODULE$").get(null)
      moduleClass.getMethod("roundTrip", classOf[String]).invoke(module, frame).asInstanceOf[String]
    }
    try {
      val fromFirst = exchange(first, golden)
      val fromSecond = exchange(second, fromFirst)
      if (fromFirst != golden || fromSecond != golden) throw new IllegalStateException("Protocol exchange requires an unchanged string frame")
      println("PROTOCOL_CLASSLOADER_CONSUMER_OK isolated=2 exchange=String")
    } finally {
      first.close()
      second.close()
    }
  }
}
