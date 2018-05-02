package com.github.pshirshov.izumi

class DummyFileServiceImpl(override val path: String) extends FileService[DummyFile] {
  override val createFileWithName: String => DummyFile = DummyFile.apply
}