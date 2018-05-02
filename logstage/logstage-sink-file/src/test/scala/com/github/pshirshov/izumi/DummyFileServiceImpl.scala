package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileSink.FileIdentity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.Discarder

import scala.collection.mutable
import scala.util.{Success, Try}

class DummyFileServiceImpl(override val path: String) extends FileService[DummyFile] {
  override val createFileWithName: String => DummyFile = DummyFile.apply
}