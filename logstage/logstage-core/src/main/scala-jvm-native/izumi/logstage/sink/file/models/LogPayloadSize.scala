package izumi.logstage.sink.file.models

import java.nio.charset.StandardCharsets

trait LogPayloadSize {

  def size(payload: String): Int
}

object LogPayloadSize {

  object LogPayloadSizeSoft extends LogPayloadSize {
    override def size(payload: String): Int = 1
  }

  object LogPayloadSizeInBytes extends LogPayloadSize {
    override def size(payload: String): Int = {
      payload.getBytes(StandardCharsets.UTF_8).length
    }
  }

}
