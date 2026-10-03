package izumi.distage.framework.services

import io.circe.JsonObject
import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.ConfigSource

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.Try

trait ConfigSourceReader {
  def read(source: ConfigSource): Option[String]
}

object ConfigSourceReader {
  final class LocalFSImpl(classLoader: ClassLoader) extends ConfigSourceReader {
    override def read(source: ConfigSource): Option[String] = source match {
      case ConfigSource.File(file) =>
        val path = Paths.get(file)
        if (Files.exists(path)) Some(Files.readString(path, StandardCharsets.UTF_8)) else None
      case ConfigSource.Resource(name) =>
        Option(classLoader.getResourceAsStream(name)).map {
          stream =>
            try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
            finally stream.close()
        }
    }
  }

  def parse(content: String): Try[DistageConfigImpl] = {
    io.circe.parser.parse(content).flatMap(_.as[JsonObject]).toTry
  }
}
