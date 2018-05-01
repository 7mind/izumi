package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.models.FileRotation
import org.scalatest.WordSpec
import FileSinkLogicTest._
import scala.util.Random
import org.scalatest.GivenWhenThen

@ExposedTestScope
class FileSinkLogicTest extends WordSpec with GivenWhenThen{

  "Dummy file sink " should {

    val dummyFolder = "folder"

    "write data to files correctly" in {
      withSink(withoutRotation(2, dummyFolder)) {
        sink =>
          List.fill(3)("msg") foreach sink.sendMessage
          val curState = sink.sinkState.get()
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState.currentFileSize == 1)
          assert(sink.fileService.getFileIdsIn(dummyFolder).map(_.size).toOption.contains(2))
      }
    }

    "continue writing data starts from existing non-empty file" in {

      val prefilledFiles = new DummyFileService()
      val randomFileSize = Random.nextInt(100)

      val file1 = DummyFile(FileSink.buildFileName(dummyFolder, FileSink.FileIdentity.init))

      Given("empty file in storage")
      prefilledFiles.storage.put(dummyFolder, Set(file1))

      withSink(withoutRotation(randomFileSize, dummyFolder, prefilledFiles)) {
        sink =>
          When("new message sends")
          sink.sendMessage("new")
          val curState = sink.sinkState.get()
          Then("current file id should be init and size uquals to 1")
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init))
          assert(curState.currentFileSize == 1)
      }

      (1 to randomFileSize).foreach {
        i => file1.append(s"msg$i")
      }

      val file2 = DummyFile(FileSink.buildFileName(dummyFolder, FileSink.FileIdentity.init + 1))

      val lastPos = Random.nextInt(randomFileSize - 1) // last position exclude
      (1 to lastPos).foreach {
        i =>
          file2.append(s"msg$i")
      }

      Given("full and randomly filled files in storage")
      prefilledFiles.storage.put(dummyFolder, Set(file1, file2))


      withSink(withoutRotation(randomFileSize, dummyFolder, prefilledFiles)) {
        sink =>
          When("new message sends")
          sink.sendMessage("new")
          val curState = sink.sinkState.get()
          Then("current file id should be next and size uquals to next after size of randomly filled file")
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState.currentFileSize == lastPos + 1)
      }

      Given("fullfilled file in storage")
      prefilledFiles.storage.put(dummyFolder, Set(file1))

      withSink(withoutRotation(randomFileSize, dummyFolder, prefilledFiles)) {
        sink =>
          When("new message sends")
          sink.sendMessage("new")
          Then("current file id should be next and size uquals to 1")
          val curState = sink.sinkState.get()
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState.currentFileSize == 1)
      }
    }

    "perform file limiter rotation" in {

      val fileSize = 2
      val filesLimit = 3

      val dummyService = new DummyFileService()
      withSink(withRotation(fileSize = fileSize, folder = dummyFolder, filesLimit = filesLimit, fileService = dummyService)) {
        sink =>
          (1 to fileSize * filesLimit).foreach {
            i =>
              sink.sendMessage(i.toString)
          }
          val curState1 = sink.sinkState.get()
          assert(curState1.forRotate.isEmpty)
          assert(curState1.currentFileId.contains(FileSink.FileIdentity.init + 2))

          sink.sendMessage("another")
          val curState2 = sink.sinkState.get()
          assert(curState2.forRotate.size == filesLimit - 1)
          assert(curState2.currentFileId.contains(FileSink.FileIdentity.init))

          (1 to fileSize).foreach {
            i =>
              sink.sendMessage(i.toString)
          }
          val curState3 = sink.sinkState.get()
          assert(curState3.forRotate.size == filesLimit - 2)
          assert(curState3.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState3.currentFileSize == 1)
      }
    }


    "perform continuing writing when rotation is enabled " in {

      val fileSize = 2
      val filesLimit = 3

      val prefilledFiles = new DummyFileService()
      val randomFileSize = Random.nextInt(100)

      val filledFiles = (0 until filesLimit).map {
        i =>
          val file = DummyFile(FileSink.buildFileName(dummyFolder, FileSink.FileIdentity.init + i))
          ( 1 to fileSize).map(_.toString) foreach file.append
          file
      }

      prefilledFiles.storage.put(dummyFolder, filledFiles.toSet)

      withSink(withRotation(fileSize = fileSize, folder = dummyFolder, filesLimit = filesLimit, fileService = prefilledFiles)) {
        sink =>
          sink.sendMessage("another")
          val curState = sink.sinkState.get()
          assert(curState.forRotate.size == filesLimit - 1)
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init))
      }
    }

  }
}

object FileSinkLogicTest {

  def dummySink(r: FileRotation, fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService()): FileSink = {
    new FileSink(fileService, r, FileSinkConfig(fileSize, folder))
  }

  def withoutRotation(fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService()): FileSink = {
    dummySink(FileRotation.DisabledRotation, fileSize, folder, fileService)
  }

  def withRotation(fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService(), filesLimit: Int): FileSink = {
    dummySink(FileRotation.FileLimiterRotation(filesLimit), fileSize, folder, fileService)
  }

  def withSink(f: => FileSink)(f2: FileSink => Unit): Unit = {
    f2(f)
  }

}