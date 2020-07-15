package izumi.dummy

import izumi.logstage.sink.file.models.LogFile

import scala.collection.mutable.ListBuffer

case class DummyFile(override val name: String) extends LogFile {

  private val content = ListBuffer.empty[String]

  override def exists: Boolean = true

  def size: Int = {
    content.size
  }

  def getContent: List[String] = {
    content.toList
  }

  def append(i: String): Unit = {
    content += i
  }

  override def beforeDelete(): Unit = {}
}
