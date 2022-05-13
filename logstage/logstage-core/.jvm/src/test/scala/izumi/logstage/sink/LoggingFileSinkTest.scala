package izumi.logstage.sink

import izumi.dummy.{DummyFile, DummyFileServiceImpl}
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.IzLogger
import izumi.logstage.api.rendering.RenderingPolicy
import izumi.logstage.sink.FileServiceUtils._
import izumi.logstage.sink.LoggingFileSinkTest.{FileSinkBrokenImpl, randomInt, _}
import izumi.logstage.sink.file.FileServiceImpl.RealFile
import izumi.logstage.sink.file.FileSink.FileIdentity
import izumi.logstage.sink.file.models.{FileRotation, FileSinkConfig, FileSinkState, LogFile}
import izumi.logstage.sink.file.{FileService, FileServiceImpl, FileSink}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Assertion, GivenWhenThen}

import scala.collection.mutable.ListBuffer
import scala.util.{Random, Try}

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
trait LoggingFileSinkTest[T <: LogFile] extends AnyWordSpec with GivenWhenThen {

  def fileSvcUtils: FileServiceUtils[T]

  "File sink" should {

    val policy = RenderingPolicy.simplePolicy()

    val dummyFolder = "target/logstage"

    "write data to files correctly" in {

      val svc = fileSvcUtils.provideSvc(dummyFolder)

      withFileLogger(withoutRotation(policy, 2, svc)) {
        (sink, logger) =>
          List.fill(3)("msg").foreach(i => logger.info(s"dummy message: $i"))
          val curState = sink.sinkState.get()
          assert(curState.currentFileId == 1)
          assert(curState.currentFileSize == 1)
          assert(sink.fileService.scanDirectory.size == 2)
      }
    }

    "continue writing data starts from existing non-empty file" in {

      val prefilledFiles = fileSvcUtils.provideSvc(dummyFolder)
      val randomFileSize = randomInt() + 1

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

      val lastPos = randomInt(randomFileSize - 1)

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
            i => logger.info(s"dummy message: $i")
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
              logger.info(s"dummy message: $i")
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
        (0 until filesLimit).map {
          i => (i, (1 to fileSize).map(idx => idx.toString).toList)
        }.toList
      }

      withFileLogger(withRotation(policy, fileSize = fileSize, filesLimit = filesLimit, fileService = svc)) {
        (sink, logger) =>
          logger.info("new")
          val curState = sink.sinkState.get()
          assert(curState.forRotate.size == filesLimit - 1)
          assert(curState.currentFileId == 0)
      }
    }

    "recover when error on writing occurred" in {

      val fileSize = randomInt()

      val svc = fileSvcUtils.provideSvc(dummyFolder)

      withFileLogger(withoutRotation(policy, fileSize = fileSize, fileService = svc, broken = true)) {
        (sink, logger) =>
          0 until fileSize foreach {
            i => logger.info(s"dummy $i")
          }
          assert(sink.asInstanceOf[FileSinkBrokenImpl[T]].recoveredMessages.lengthCompare(2 * fileSize) == 0)
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

  private val maxRandom = 100

  def randomInt(until: Int = maxRandom): Int =
    if (until == 0) {
      0
    } else {
      Random.nextInt(until)
    }

  def dummySink[F <: LogFile](
    renderingPolicy: RenderingPolicy,
    r: FileRotation,
    fileSize: Int,
    fileService: FileService[F],
    broken: Boolean,
    softLimit: Boolean,
  ): FileSink[F] = {

    val cfg = if (softLimit) {
      FileSinkConfig.soft(_: Int)
    } else {
      FileSinkConfig.inBytes(_: Int)
    }(fileSize)

    if (broken) {
      new FileSinkBrokenImpl(renderingPolicy, fileService, r, cfg)
    } else {
      new FileSinkTestImpl(renderingPolicy, fileService, r, cfg)
    }
  }

  def withoutRotation[F <: LogFile](
    renderingPolicy: RenderingPolicy,
    fileSize: Int,
    fileService: FileService[F],
    broken: Boolean = false,
    softLimit: Boolean = true,
  ): FileSink[F] = {
    dummySink(renderingPolicy, FileRotation.DisabledRotation, fileSize, fileService, broken, softLimit)
  }

  def withRotation[F <: LogFile](
    renderingPolicy: RenderingPolicy,
    fileSize: Int,
    fileService: FileService[F],
    filesLimit: Int,
    broken: Boolean = false,
    softLimit: Boolean = true,
  ): FileSink[F] = {
    dummySink(renderingPolicy, FileRotation.FileLimiterRotation(filesLimit), fileSize, fileService, broken, softLimit)
  }

  def withFileLogger[F <: LogFile](f: => FileSink[F])(f2: (FileSink[F], IzLogger) => Assertion): Unit = {
    val fileSink = f
    val logger = IzLogger(IzLogger.Level.Trace, fileSink)
    try {
      Quirks.discard(f2(fileSink, logger))
    } finally {
      val svc = fileSink.fileService
      svc.scanDirectory.foreach(svc.removeFile)
    }
  }

  class FileSinkTestImpl[F <: LogFile](
    override val renderingPolicy: RenderingPolicy,
    override val fileService: FileService[F],
    override val rotation: FileRotation,
    override val config: FileSinkConfig,
  ) extends FileSink[F](renderingPolicy, fileService, rotation, config) {

    override def recoverOnFail(e: String): Unit = println()
  }

  class FileSinkBrokenImpl[F <: LogFile](
    override val renderingPolicy: RenderingPolicy,
    override val fileService: FileService[F],
    override val rotation: FileRotation,
    override val config: FileSinkConfig,
  ) extends FileSink[F](renderingPolicy, fileService, rotation, config) {

    val recoveredMessages: ListBuffer[String] = ListBuffer.empty[String]

    override def recoverOnFail(e: String): Unit = {
      recoveredMessages += e
    }

    override def performWriting(state: FileSinkState, payload: String): Try[FileSinkState] = {
      Try {
        throw new Exception("Error while writing to file sink")
      }
    }
  }

}
