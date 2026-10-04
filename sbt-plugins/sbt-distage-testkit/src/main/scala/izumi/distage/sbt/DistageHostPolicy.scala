package izumi.distage.sbt

import izumi.distage.testkit.protocol.{BuildId, BuildTargetId, CatalogueId, CatalogueIdentity, RequestArguments}

import sbt.{TestDefinition, TestFramework, TestOption, Tests}
import sbt.testing.SubclassFingerprint
import sbt.util.Logger

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private[sbt] object DistageHostPolicy {
  val framework = new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")
  val inspectionLauncher = "izumi.distage.testkit.runner.InspectionLauncher"
  private final val SuiteSuperclass = "izumi.distage.testkit.runner.TestSuite"
  private val IdentityOptions = Set("--build-id", "--target-id", "--catalogue-id")

  def isDistage(definition: TestDefinition): Boolean = definition.fingerprint match {
    case fingerprint: SubclassFingerprint => !fingerprint.isModule() && fingerprint.superclassName() == SuiteSuperclass
    case _ => false
  }

  def catalogueId(definitions: Seq[TestDefinition]): String = {
    val names = definitions.filter(isDistage).map(_.name).sorted
    val hash = MessageDigest.getInstance("SHA-256")
    names.foreach { name =>
      hash.update(name.getBytes(StandardCharsets.UTF_8))
      hash.update(0.toByte)
    }
    hash.digest().map(byte => f"${byte & 0xff}%02x").mkString
  }

  def arguments(build: String, target: String, catalogue: String): Seq[String] = {
    require(build.nonEmpty && target.nonEmpty && catalogue.nonEmpty, "Distage host identities must not be empty")
    val identity = CatalogueIdentity(BuildId(build), BuildTargetId(target), CatalogueId(catalogue))
    Seq("--build-id", identity.build.value, "--target-id", identity.target.value, "--catalogue-id", identity.catalogue.value)
  }

  def withIdentity(options: Seq[TestOption], arguments: Seq[String]): Seq[TestOption] = {
    val retained = options.filterNot {
      case Tests.Argument(Some(owner), values) if owner == framework =>
        values.size == IdentityOptions.size * 2 && values.grouped(2).map(_.head).toSet == IdentityOptions
      case _ => false
    }
    retained :+ Tests.Argument(framework, arguments: _*)
  }

  def inspectionArguments(operation: String, identities: Seq[String], options: Seq[String], definitions: Seq[TestDefinition]): Seq[String] = {
    val suites = definitions.filter(isDistage).map(_.name).sorted
    require(suites.nonEmpty, "Distage inspection found no suite definitions")
    val request = RequestArguments.parse((identities ++ options).toVector).fold(error => throw new IllegalArgumentException(error.message), value => value)
    Vector(operation) ++ RequestArguments.render(request) ++ Vector("--") ++ suites
  }

  def conservativeFilter(
    definitions: Seq[TestDefinition],
    inherited: Seq[String] => Seq[String => Boolean],
    selection: Seq[String] => Seq[String => Boolean],
    log: Logger,
  ): Seq[String] => Seq[String => Boolean] = {
    val owned = definitions.filter(isDistage).map(_.name).toSet
    arguments => {
      val selected = selection(arguments.takeWhile(_ != "--"))
      val stock = inherited(arguments)
      val rerun = owned.filter(name => selected.exists(_(name)))
      rerun.toVector.sorted.foreach { name =>
        log.info("DISTAGE_CACHE_DECISION suite=" + name + " decision=rerun reason=untracked-input-closure")
      }
      Seq(name => rerun.contains(name) || (!owned.contains(name) && stock.exists(_(name))))
    }
  }
}
