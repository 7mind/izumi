package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.models.FileRotation
import org.scalatest.WordSpec
import LoggingFileSinkTest._
import com.github.pshirshov.izumi.logstage.api.logger.RenderingPolicy
import com.github.pshirshov.izumi.{DummyFile, DummyFileService, FileSink, FileSinkConfig}

import scala.util.Random
import org.scalatest.GivenWhenThen

@ExposedTestScope
class LoggingFileSinkTest extends WordSpec with GivenWhenThen{


  "Dummy file sink " should {


    val policy = LoggingMacroTest.simplePolicy

    val dummyFolder = "folder"

    "write data to files correctly" in {
      withFileLogger(withoutRotation(policy, 2, dummyFolder)) {
        (sink, logger) =>

          List.fill(3)("msg").foreach(i => logger.info(i))
          val curState = sink.sinkState.get()
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState.currentFileSize == 1)
          assert(sink.fileService.getFileIds.map(_.size).toOption.contains(2))
      }
    }

    "continue writing data starts from existing non-empty file" in {

      val prefilledFiles = new DummyFileService()
      val randomFileSize = Random.nextInt(100)

      val file1 = DummyFile(FileSink.buildFileName(dummyFolder, FileSink.FileIdentity.init))

      Given("empty file in storage")
      prefilledFiles.storage.put(FileSink.FileIdentity.init, file1)

      withFileLogger(withoutRotation(policy, randomFileSize, dummyFolder, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
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
      prefilledFiles.storage.clear()
      prefilledFiles.storage.put(FileSink.FileIdentity.init, file1)
      prefilledFiles.storage.put(FileSink.FileIdentity.init + 1, file2)


      withFileLogger(withoutRotation(policy, randomFileSize, dummyFolder, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
          val curState = sink.sinkState.get()
          Then("current file id should be next and size uquals to next after size of randomly filled file")
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init + 1))
          assert(curState.currentFileSize == lastPos + 1)
      }

      Given("fullfilled file in storage")
      prefilledFiles.storage.clear()
      prefilledFiles.storage.put(FileSink.FileIdentity.init, file1)

      withFileLogger(withoutRotation(policy, randomFileSize, dummyFolder, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
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
      withFileLogger(withRotation(policy, fileSize = fileSize, folder = dummyFolder, filesLimit = filesLimit, fileService = dummyService)) {
        (sink, logger) =>
          (1 to fileSize * filesLimit).foreach {
            i =>
              logger.info(i.toString)
          }
          val curState1 = sink.sinkState.get()
          assert(curState1.forRotate.isEmpty)
          assert(curState1.currentFileId.contains(FileSink.FileIdentity.init + 2))

          logger.info("new")
          val curState2 = sink.sinkState.get()
          assert(curState2.forRotate.size == filesLimit - 1)
          assert(curState2.currentFileId.contains(FileSink.FileIdentity.init))

          (1 to fileSize).foreach {
            i =>
              logger.info(i.toString)
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
          (FileSink.FileIdentity.init + i, file)
      }

      filledFiles.foreach {
        case (id, file) =>
          prefilledFiles.storage.put(id, file)
      }

      withFileLogger(withRotation(policy, fileSize = fileSize, folder = dummyFolder, filesLimit = filesLimit, fileService = prefilledFiles)) {
        (sink, logger) =>
          logger.info("new")
          val curState = sink.sinkState.get()
          assert(curState.forRotate.size == filesLimit - 1)
          assert(curState.currentFileId.contains(FileSink.FileIdentity.init))
      }
    }

  }
}

object LoggingFileSinkTest {

  def dummySink(renderingPolicy: RenderingPolicy, r: FileRotation, fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService()): FileSink = {
    new FileSink(renderingPolicy, fileService, r, FileSinkConfig(fileSize, folder))
  }

  def withoutRotation(renderingPolicy: RenderingPolicy,fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService()): FileSink = {
    dummySink(renderingPolicy, FileRotation.DisabledRotation, fileSize, folder, fileService)
  }

  def withRotation(renderingPolicy: RenderingPolicy,fileSize: Int, folder: String = "", fileService: DummyFileService = new DummyFileService(), filesLimit: Int): FileSink = {
    dummySink(renderingPolicy, FileRotation.FileLimiterRotation(filesLimit), fileSize, folder, fileService)
  }

  def withFileLogger(f: => FileSink)(f2: (FileSink, IzLogger) => Unit): Unit = {
    val fileSink = f
    val logger = LoggingMacroTest.configureLogger(Seq(fileSink))
    f2(fileSink, logger)
  }

}