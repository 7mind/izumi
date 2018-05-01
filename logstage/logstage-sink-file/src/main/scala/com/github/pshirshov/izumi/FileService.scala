package com.github.pshirshov.izumi

import java.nio.file.Paths

import scala.util.Try

trait FileService {

  def getFileIdsIn(fileName: String): Try[Set[String]]

  def fileContent(fileName: String): Try[List[String]]

  def fileSize(fileName: String): Try[Int]

  def clearFile(fileName: String): Try[Unit]

  def removeFile(fileName: String): Try[Unit]

  def writeToFile(fileName: String, content: String): Try[Unit]

  def getFileId(fullName: String): Try[Int]

}

object FileService {
  def parseFileName(fileName: String): Try[(String, String)] = {
    for {
      res <- Try(Paths.get(fileName))
    } yield (res.getParent.toString, res.getFileName.toString)
  }
}
