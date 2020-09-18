package izumi.logstage.api.rendering

trait LogstageWriterResult[T] {
  def translate(): T
}
