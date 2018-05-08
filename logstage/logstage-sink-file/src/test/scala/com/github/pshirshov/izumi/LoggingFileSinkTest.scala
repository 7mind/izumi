package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.FileServiceImpl.RealFile
import com.github.pshirshov.izumi.FileServiceUtils._
import com.github.pshirshov.izumi.FileSink.FileIdentity
import com.github.pshirshov.izumi.LoggingFileSinkTest._
import com.github.pshirshov.izumi.dummy.{DummyFile, DummyFileServiceImpl}
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy
import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest
import com.github.pshirshov.izumi.models.{FileRotation, LogFile}
import org.scalatest.{Assertion, GivenWhenThen, WordSpec}

import scala.util.Random


trait FileServiceUtils[T <: LogFile] {

  def provideSvc(path: String): FileService[T]
}

object FileServiceUtils {

  implicit class FileServiceOps[T <: LogFile](svc: FileService[T]) {
    def withPreparedData(f: => List[(FileIdentity, List[String])]): Unit = {
      val st = svc.storage
      st.clear()
      f.foreach {
        case (id, msgs) =>
          val file = svc.createFile(id)
          msgs foreach file.append
          st.put(id, file)
      }
    }
  }

}

@ExposedTestScope
trait LoggingFileSinkTest[T <: LogFile] extends WordSpec with GivenWhenThen {

  def fileSvcUtils: FileServiceUtils[T]

  "File sink" should {

    val policy = LoggingMacroTest.simplePolicy()

    val dummyFolder = "logstage"

    "write data to files correctly" in {

      val svc = fileSvcUtils.provideSvc(dummyFolder)

      withFileLogger(withoutRotation(policy, 2, svc)) {
        (sink, logger) =>
          List.fill(3)("msg").foreach(i => logger.info(i))
          val curState = sink.sinkState.get()
          assert(curState.currentFileId == 1)
          assert(curState.currentFileSize == 1)
          assert(sink.fileService.scanDirectory.size == 2)
      }
    }

    "continue writing data starts from existing non-empty file" in {

      val prefilledFiles = fileSvcUtils.provideSvc(dummyFolder)
      val randomFileSize = Random.nextInt(100) + 1

      Given("empty file in storage")
      prefilledFiles.withPreparedData {
        List((0, List.empty))
      }

      withFileLogger(withoutRotation(policy, randomFileSize, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
          val curState = sink.sinkState.get()
          Then("current file id should be init and size uquals to 1")
          assert(curState.currentFileId == 0)
          assert(curState.currentFileSize == 1)
      }

      val lastPos = Random.nextInt(randomFileSize - 1)

      Given("full and randomly filled files in storage")

      val fullFile = (0, (1 to randomFileSize).map(i => s"msg$i").toList)
      val randomlyFilledData = (1, (1 to lastPos).map(i => s"msg$i").toList)

      prefilledFiles.withPreparedData {
        List(fullFile, randomlyFilledData)
      }

      withFileLogger(withoutRotation(policy, randomFileSize, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
          val curState = sink.sinkState.get()
          Then("current file id should be next and size uquals to next after size of randomly filled file")
          assert(curState.currentFileId == 1)
          assert(curState.currentFileSize == lastPos + 1)
      }

      Given("fullfilled file in storage")
      prefilledFiles.withPreparedData {
        List(fullFile)
      }

      withFileLogger(withoutRotation(policy, randomFileSize, prefilledFiles)) {
        (sink, logger) =>
          When("new message sends")
          logger.info("new")
          Then("current file id should be next and size uquals to 1")
          val curState = sink.sinkState.get()
          assert(curState.currentFileId == 1)
          assert(curState.currentFileSize == 1)
      }
    }

    "perform file limiter rotation" in {

      val fileSize = 2
      val filesLimit = 3

      val svc = fileSvcUtils.provideSvc(dummyFolder)

      withFileLogger(withRotation(policy, fileSize = fileSize, filesLimit = filesLimit, fileService = svc)) {
        (sink, logger) =>
          (1 to fileSize * filesLimit).foreach {
            i => logger.info(i.toString)
          }
          val curState1 = sink.sinkState.get()
          assert(curState1.forRotate.isEmpty)
          assert(curState1.currentFileId == 2)

          logger.info("new")
          val curState2 = sink.sinkState.get()
          assert(curState2.forRotate.size == filesLimit - 1)
          assert(curState2.currentFileId == 0)

          (1 to fileSize).foreach {
            i =>
              logger.info(i.toString)
          }
          val curState3 = sink.sinkState.get()
          assert(curState3.forRotate.size == filesLimit - 2)
          assert(curState3.currentFileId == 1)
          assert(curState3.currentFileSize == 1)
      }
    }


    "perform continuing writing when rotation is enabled " in {

      val fileSize = 2
      val filesLimit = 3

      val svc = fileSvcUtils.provideSvc(dummyFolder)

      svc.withPreparedData {
        (0 until filesLimit).map { i => (i, (1 to fileSize).map(_.toString).toList) }.toList
      }

      withFileLogger(withRotation(policy, fileSize = fileSize, filesLimit = filesLimit, fileService = svc)) {
        (sink, logger) =>
          logger.info("new")
          val curState = sink.sinkState.get()
          assert(curState.forRotate.size == filesLimit - 1)
          assert(curState.currentFileId == 0)
      }
    }

  }
}

class DummyFileSinkTest extends LoggingFileSinkTest[DummyFile] {
  override val fileSvcUtils: FileServiceUtils[DummyFile] = (path: String) => new DummyFileServiceImpl(path)
}


class RealFileSinkTest extends LoggingFileSinkTest[RealFile] {
  override val fileSvcUtils: FileServiceUtils[RealFile] = (path: String) => new FileServiceImpl(path)
}


object LoggingFileSinkTest {

  def dummySink[F <: LogFile](renderingPolicy: RenderingPolicy, r: FileRotation, fileSize: Int, fileService: FileService[F]): FileSink[F] = {
    new FileSink(renderingPolicy, fileService, r, FileSinkConfig(fileSize))
  }

  def withoutRotation[F <: LogFile](renderingPolicy: RenderingPolicy, fileSize: Int, fileService: FileService[F]): FileSink[F] = {
    dummySink(renderingPolicy, FileRotation.DisabledRotation, fileSize, fileService)
  }

  def withRotation[F <: LogFile](renderingPolicy: RenderingPolicy, fileSize: Int, fileService: FileService[F], filesLimit: Int): FileSink[F] = {
    dummySink(renderingPolicy, FileRotation.FileLimiterRotation(filesLimit), fileSize, fileService)
  }

  def withFileLogger[F <: LogFile](f: => FileSink[F])(f2: (FileSink[F], IzLogger) => Assertion): Unit = {
    val fileSink = f
    val logger = LoggingMacroTest.configureLogger(Seq(fileSink))
    try {
      f2(fileSink, logger)
    } finally {
      val svc = fileSink.fileService
      svc.scanDirectory.foreach(svc.removeFile)
    }
  }

}