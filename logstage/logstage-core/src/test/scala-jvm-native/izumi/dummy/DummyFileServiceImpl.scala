package izumi.dummy

import izumi.logstage.sink.file.FileService

class DummyFileServiceImpl(override val path: String) extends FileService[DummyFile] {
  override val createFileWithName: String => DummyFile = DummyFile.apply
}
