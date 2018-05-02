package com.github.pshirshov.izumi.models

import scala.util.Try

trait LogFile {
  def name : String
  def size : Try[Int]
  def getContent : Try[Iterable[String]]
  def append(item : String) : Try[Unit]
  def beforeDelete() : Try[Unit]
  def exists : Boolean
}

