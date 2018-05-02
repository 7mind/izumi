package com.github.pshirshov.izumi

import java.nio.file.Paths

import com.github.pshirshov.izumi.FileSink.FileIdentity

import scala.util.Try

trait FileService {

  def getFileIds: Try[Set[FileIdentity]]

  def fileContent(fileIdentity: FileIdentity): Try[List[String]]

  def fileSize(fileIdentity: FileIdentity): Try[Int]

  def clearFile(fileIdentity: FileIdentity): Try[Unit]

  def removeFile(fileIdentity: FileIdentity): Try[Unit]

  def writeToFile(path : String, fileIdentity: FileIdentity, content: String): Try[Unit]

}

object FileService {
  def parseFileName(fileName: String): Try[(String, String)] = {
    for {
      res <- Try(Paths.get(fileName))
    } yield (res.getParent.toString, res.getFileName.toString)
  }
}
