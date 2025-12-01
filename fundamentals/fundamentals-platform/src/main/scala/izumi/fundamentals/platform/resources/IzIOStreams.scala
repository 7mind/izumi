package izumi.fundamentals.platform.resources

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.util.stream.Collectors

object IzIOStreams {
  implicit final class ISTool(private val is: InputStream) {
    def streamToString(): String = {
      val reader = new BufferedReader(new InputStreamReader(is))
      try {
        reader.lines.collect(Collectors.joining(System.lineSeparator))
      } finally {
        reader.close()
      }
    }
  }
}
