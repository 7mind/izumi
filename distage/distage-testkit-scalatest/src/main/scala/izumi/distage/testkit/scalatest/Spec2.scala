package izumi.distage.testkit.scalatest

import distage.DefaultModule2
import izumi.distage.constructors.ClassConstructor
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec
import izumi.distage.testkit.services.scalatest.dstest.DistageAbstractScalatestSpec.DSWordSpecStringWrapper2
import izumi.fundamentals.platform.language.SourceFilePositionMaterializer
import izumi.logstage.distage.LogIO2Module
import izumi.reflect.{Tag, TagKK}
import org.scalatest.distage.DistageScalatestTestSuiteRunner

import scala.language.implicitConversions

abstract class Spec2[F[+_, +_]: DefaultModule2](implicit val tagBIO: TagKK[F])
  extends DistageScalatestTestSuiteRunner[F[Throwable, _]]
  with DistageAbstractScalatestSpec[F[Throwable, _]] {

  protected implicit def convertToWordSpecStringWrapperDS2(s: String): DSWordSpecStringWrapper2[F] = {
    new DSWordSpecStringWrapper2(context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv)
  }

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = LogIO2Module[F]()(tagBIO)
  )
}

abstract class SpecCtx2[F[+_, +_], Ctx: ClassConstructor: Tag](implicit defaultModule: DefaultModule2[F], tagBIO: TagKK[F]) extends Spec2[F] { x =>

//  implicit class SuperXa(s: String) {
//    def in(function: Ctx => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
//      x.convertToWordSpecStringWrapperDS2(s).in(ClassConstructor[Ctx].map(function))
//    }
//  }

  trait XWrapper extends DSWordSpecStringWrapper2[F] {
    def in(function: Ctx => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
      in(ClassConstructor[Ctx].map(function))
    }
  }

  override protected implicit def convertToWordSpecStringWrapperDS2(s: String): XWrapper =
    new DSWordSpecStringWrapper2[F](context, distageSuiteName, distageSuiteId, Seq(s), this, testEnv) with XWrapper {
      override def in(function: Ctx => F[Any, Unit])(implicit pos: SourceFilePositionMaterializer): Unit = {
        in(ClassConstructor[Ctx].map(function))
      }
    }

}

import zio.*
case class Ctx(i: Int)
class Test extends SpecCtx2[IO, Ctx] {

  "test" should {
    "abc" in {
      ctx =>
        for {
          _ <- ZIO.consoleWith(_.print("abc"))
        } yield ()
    }

    "xn" in {
      (_: Int) => ZIO.unit
    }
  }

}
