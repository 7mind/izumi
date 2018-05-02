package com.github.pshirshov.izumi.dummy

import com.github.pshirshov.izumi.FileService

class DummyFileServiceImpl(override val path: String) extends FileService[DummyFile] {
  override val createFileWithName: String => DummyFile = DummyFile.apply
}