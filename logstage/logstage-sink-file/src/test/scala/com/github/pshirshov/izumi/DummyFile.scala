package com.github.pshirshov.izumi

import scala.collection.mutable.ListBuffer

case class DummyFile(name: String) {

  private val _content = ListBuffer.empty[String]

  def size: Int = {
    content.size
  }

  def content: List[String] = _content.toList

  def append(i: String): Unit = {
    _content += i
  }

  def clear: Unit = _content.clear()
}