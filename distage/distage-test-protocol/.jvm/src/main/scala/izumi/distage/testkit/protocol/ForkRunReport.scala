package izumi.distage.testkit.protocol

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Base64
import scala.jdk.CollectionConverters._

final case class ForkSuiteOwner(name: String, id: Option[SuiteId]) {
  require(name.nonEmpty && StandardCharsets.UTF_8.newEncoder().canEncode(name), "Invalid fork suite name")
}
final case class ForkRunReport(outcome: RunOutcome, owners: Vector[ForkSuiteOwner]) {
  require(owners.map(_.name).distinct.size == owners.size, "Duplicate fork suite names")
  require(outcome.results.forall(result => owners.count(_.id.contains(result.id.suite)) == 1), "Fork result has no unique suite owner")
}

trait ForkRunReports {
  def publish(report: ForkRunReport): Unit
  def completed(): Vector[ForkRunReport]
}

final class FileForkRunReports(directory: Path) extends ForkRunReports {
  private final val Suffix = ".run-report"
  private final val Version = "1"
  require(directory.isAbsolute && Files.isDirectory(directory), "Fork report directory must be an existing absolute path")

  override def publish(report: ForkRunReport): Unit = {
    val destination = directory.resolve(report.outcome.run.value + Suffix)
    require(destination.getParent == directory && !Files.exists(destination), "Invalid or duplicate fork run report")
    val lines = Vector(Version, ProtocolCodec.encode(ProtocolMessage.Completed(report.outcome))) ++ report.owners.map { owner =>
      encode(owner.name) + "\t" + owner.id.fold("")(id => encode(id.value))
    }
    val temporary = Files.createTempFile(directory, "fork-run-", ".tmp")
    try {
      val _ = Files.write(temporary, lines.asJava, StandardCharsets.UTF_8)
      val _ = Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
    } finally { val _ = Files.deleteIfExists(temporary) }
  }

  override def completed(): Vector[ForkRunReport] = {
    val entries = Files.list(directory)
    try entries.iterator().asScala.filter(_.getFileName.toString.endsWith(Suffix)).map { path =>
      val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      require(lines.size >= 2 && lines.head == Version, "Invalid fork run report schema")
      val outcome = ProtocolCodec.decode(lines(1)).fold(error => throw new IllegalArgumentException(error.message), {
        case ProtocolMessage.Completed(value) => value
        case _ => throw new IllegalArgumentException("Fork report has no terminal outcome")
      })
      require(path.getFileName.toString == outcome.run.value + Suffix, "Fork report filename differs from its run")
      val owners = lines.drop(2).map { line =>
        val fields = line.split("\t", -1)
        require(fields.length == 2, "Invalid fork suite binding")
        ForkSuiteOwner(decode(fields(0)), if (fields(1).isEmpty) None else Some(SuiteId(decode(fields(1)))))
      }
      ForkRunReport(outcome, owners)
    }.toVector finally entries.close()
  }

  private def encode(value: String): String = Base64.getUrlEncoder.withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))
  private def decode(value: String): String = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.getUrlDecoder.decode(value))).toString
}
