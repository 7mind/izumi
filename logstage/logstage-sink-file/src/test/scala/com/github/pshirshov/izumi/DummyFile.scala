package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.models.LogFile

import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}

case class DummyFile(override val name: String) extends LogFile {

  private val content = ListBuffer.empty[String]

  override def exists: Boolean = true

  def size: Try[Int] = {
    Success(content.size)
  }

  def getContent: Try[List[String]] = Success {
    content.toList
  }

  def append(i: String): Try[Unit] = {
    Success {
      content += i
    }
  }

  override def beforeDelete(): Try[Unit] = {
    Success(())
  }
}