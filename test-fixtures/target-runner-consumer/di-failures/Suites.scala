package candidate

import izumi.distage.testkit.runner.spec.{AnyWordSpec, Spec1, SpecIdentity, SpecZIO}
import cats.effect.IO
import zio.ZIO

abstract class PlainSuite extends AnyWordSpec {
  protected def record(index: Int): Unit = println("SDK_DI_BODY suite=" + getClass.getName + " test=" + index + " effect=plain owner=plain revision=plain snapshot=plain repo=plain")
  "equal display name" should {
    "first" in record(1)
    "second" in record(2)
    "third" in record(3)
  }
}

abstract class IdentitySuite extends SpecIdentity with Configured {
  protected def record(index: Int, value: SharedResource): Unit = {
    require(value != null)
    println("SDK_DI_BODY suite=" + getClass.getName + " test=" + index + " effect=identity owner=" + value.id + " revision=" + value.revision + " snapshot=" + value.snapshot + " repo=" + value.repo)
  }
  "equal display name" should {
    "first" in { (value: SharedResource) => record(1, value) }
    "second" in { (value: SharedResource) => record(2, value) }
    "third" in { (value: SharedResource) => record(3, value) }
  }
}

final class SuiteA extends PlainSuite
final class SuiteB extends PlainSuite
final class SuiteC extends IdentitySuite
final class SuiteD extends IdentitySuite
final class SuiteE extends IdentitySuite

final class EffectCats extends Spec1[IO] with Configured {
  private def record(index: Int, value: SharedResource): Unit = {
    require(value != null)
    println("SDK_DI_BODY suite=" + getClass.getName + " test=" + index + " effect=cats owner=" + value.id + " revision=" + value.revision + " snapshot=" + value.snapshot + " repo=" + value.repo)
    if (value.snapshot == "body-failure" && index == 3) throw new IllegalStateException("SDK_DI_BODY_FAILURE")
  }
  "equal display name" should {
    "first" in { (value: SharedResource) => IO(record(1, value)) }
    "second" in { (value: SharedResource) => IO(record(2, value)) }
    "third" in { (value: SharedResource) => IO(record(3, value)) }
  }
}

final class EffectZIO extends SpecZIO with Configured {
  private def record(index: Int, value: SharedResource): Unit = {
    require(value != null)
    println("SDK_DI_BODY suite=" + getClass.getName + " test=" + index + " effect=zio owner=" + value.id + " revision=" + value.revision + " snapshot=" + value.snapshot + " repo=" + value.repo)
  }
  "equal display name" should {
    "first" in ZIO.serviceWith[SharedResource](value => record(1, value))
    "second" in { (value: SharedResource) => ZIO.serviceWith[SharedResource] { environment => require(value eq environment); record(2, value) } }
    "third" in ZIO.serviceWith[SharedResource](value => record(3, value))
  }
}
