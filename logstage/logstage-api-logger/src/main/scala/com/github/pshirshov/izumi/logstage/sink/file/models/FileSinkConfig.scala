package com.github.pshirshov.izumi.logstage.sink.file.models

import LogPayloadSize._

case class FileSinkConfig private (fileSizeInBytes: Option[Int] = None, fileSizeSoft: Option[Int] = None) {

  val (maxAllowedSize, calculateMessageSize) = {
    (fileSizeSoft, fileSizeInBytes) match {
      case (Some(size), _) => (size, LogPayloadSizeSoft.size(_ : String))
      case (_, Some(size)) => (size, LogPayloadSizeInBytes.size(_ : String))
      case (None, None) =>
        throw new IllegalArgumentException("File limit parameter should be specified")
    }
  }
}

object FileSinkConfig {
  def inBytes(limit: Int): FileSinkConfig = new FileSinkConfig(fileSizeInBytes = Some(limit))
  def soft(limit: Int): FileSinkConfig = new FileSinkConfig(fileSizeSoft = Some(limit))
}
