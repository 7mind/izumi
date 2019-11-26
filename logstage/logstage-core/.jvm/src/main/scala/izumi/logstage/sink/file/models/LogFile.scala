package izumi.logstage.sink.file.models

trait LogFile {
  def name: String
  def size: Int
  def getContent: Iterable[String]
  def append(item: String): Unit
  def beforeDelete(): Unit
  def exists: Boolean
}
