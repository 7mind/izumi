package izumi.distage.impl

import java.net.{URI, URLClassLoader}
import scala.io.Source

object OptionalDependencyIsolation {
  def run(className: String): Unit = {
    val manifest = Option(getClass.getClassLoader.getResourceAsStream("optional-dependency-classpath.txt"))
      .getOrElse(throw new IllegalStateException("Missing optional-dependency fixture classpath"))
    val source = Source.fromInputStream(manifest, "UTF-8")
    val urls = try source.getLines().map(value => URI.create(value).toURL).toArray finally source.close()
    require(urls.nonEmpty, "Optional-dependency fixture classpath must not be empty")
    val loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader)
    val thread = Thread.currentThread()
    val previousLoader = thread.getContextClassLoader
    try {
      Vector("cats.kernel.Eq", "zio.ZIO", "monix.eval.Task").foreach { name =>
        val absent = try { val _ = loader.loadClass(name); false } catch { case _: ClassNotFoundException => true }
        require(absent, "Optional dependency must be absent from the isolated runtime: " + name)
      }
      thread.setContextClassLoader(loader)
      val check = loader.loadClass(className).asSubclass(classOf[Runnable]).getDeclaredConstructor().newInstance()
      check.run()
    } finally {
      thread.setContextClassLoader(previousLoader)
      loader.close()
    }
  }
}
