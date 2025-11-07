package izumi.distage.testkit.services.scalatest.dstest

import distage.Functoid
import izumi.distage.testkit.model.*
import izumi.distage.testkit.spec.*
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.collection.mutable.ListBuffer

trait WithTestRegistration[F[_]] extends TestRegistration[F] {
  private val _registeredTests: ListBuffer[DistageTest[F]] = ListBuffer.empty[DistageTest[F]]

  override def registerTest[A](function: Functoid[F[A]], env: TestEnvironment, pos: SourceFilePosition, id: TestId, meta: SuiteMeta): Unit = {
    val test = DistageTest(function.asInstanceOf[Functoid[F[Any]]], env, TestMeta(id, pos, System.identityHashCode(function).toLong), meta)
    _registeredTests += test
  }

  override def registeredTests(): List[DistageTest[F]] = _registeredTests.toList
}
