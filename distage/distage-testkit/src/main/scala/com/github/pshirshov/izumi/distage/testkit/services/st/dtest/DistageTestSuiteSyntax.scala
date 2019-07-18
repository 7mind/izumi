package com.github.pshirshov.izumi.distage.testkit.services.st.dtest

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.services.dstest.DistageTestRunner.{DistageTest, TestId, TestMeta}
import com.github.pshirshov.izumi.distage.testkit.services.dstest.{AbstractDistageSpec, DistageTestEnvironmentProviderImpl, TestEnvironment, TestRegistration}
import com.github.pshirshov.izumi.distage.testkit.services.{DISyntaxBIOBase, DISyntaxBase}
import com.github.pshirshov.izumi.fundamentals.platform.jvm.CodePosition
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import distage.{Tag, TagK, TagKK}
import org.scalactic.source
import org.scalatest.words.StringVerbBlockRegistration

import scala.language.implicitConversions


trait WithSingletonTestRegistration[F[_]] extends AbstractDistageSpec[F] {
  protected[testkit] def registerTest(function: ProviderMagnet[F[_]], env: TestEnvironment, pos: CodePosition, id: TestId): Unit = {
    DistageTestsRegistrySingleton.register[F](DistageTest(function, env, TestMeta(id, pos)))
  }
}

trait DistageTestSuiteSyntax[F[_]] extends ScalatestWords with WithSingletonTestRegistration[F] {
  this: AbstractDistageSpec[F] =>

  import DistageTestSuiteSyntax._

  protected lazy val tenv = new DistageTestEnvironmentProviderImpl(this.getClass)
  protected lazy val logger: IzLogger = IzLogger.apply(Log.Level.Debug)("phase" -> "test")
  protected lazy val env: TestEnvironment = tenv.loadEnvironment(logger)

  protected def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)

  protected def distageSuiteId: String = this.getClass.getName


  protected[dtest] var left: String = ""
  protected[dtest] var verb: String = ""

  protected implicit val subjectRegistrationFunction: StringVerbBlockRegistration = new StringVerbBlockRegistration {
    def apply(left: String, verb: String, pos: source.Position, f: () => Unit): Unit = registerBranch(left, Some(verb), verb, "apply", 6, -2, pos, f)
  }

  private def registerBranch(description: String, childPrefix: Option[String], verb: String, methodName: String, stackDepth: Int, adjustment: Int, pos: source.Position, fun: () => Unit): Unit = {
    Quirks.discard(childPrefix, methodName, stackDepth, adjustment, pos)
    this.left = description
    this.verb = verb
    fun()
  }

  protected implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper[F] = {
    new WordSpecStringWrapper(left, verb, distageSuiteName, distageSuiteId, s, this, env)
  }
}

object DistageTestSuiteSyntax {

  class WordSpecStringWrapper[F[_]](
                                     left: String,
                                     verb: String,
                                     suiteName: String,
                                     suiteId: String,
                                     string: String,
                                     reg: TestRegistration[F],
                                     env: TestEnvironment,
                                   )
                                   (
                                     implicit val tagMonoIO: TagK[F]
                                   ) extends DISyntaxBase[F] {
    override protected def takeIO(function: ProviderMagnet[F[_]], pos: CodePosition): Unit = {
      val id = TestId(
        Seq(left, verb, string).mkString(" "),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(function, env, pos, id)
    }

    def in(function: ProviderMagnet[Any])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      takeAny(function, pos.get)
    }

    def in(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = {
      takeIO(function, pos.get)
    }

    def in[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = {
      takeFunIO(function, pos.get)
    }

    def in[T: Tag](function: T => Any)(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = {
      takeFunAny(function, pos.get)
    }
  }

  class WordSpecStringWrapper2[F[+ _, + _]](
                                             left: String,
                                             verb: String,
                                             suiteName: String,
                                             suiteId: String,
                                             string: String,
                                             reg: TestRegistration[F[Throwable, ?]],
                                             env: TestEnvironment,
                                           )
                                           (
                                             implicit val tagMonoIO: TagK[F[Throwable, ?]],
                                             val tagBIO: TagKK[F],
                                           ) extends DISyntaxBIOBase[F] {

    override protected def takeAs1(fAsThrowable: ProviderMagnet[F[Throwable, _]], pos: CodePosition): Unit = {
      val id = TestId(
        Seq(left, verb, string).mkString(" "),
        suiteName,
        suiteId,
        suiteName,
      )
      reg.registerTest(fAsThrowable, env, pos, id)
    }

    def in(function: ProviderMagnet[F[_, _]])(implicit pos: CodePositionMaterializer): Unit = {
      take2(function, pos.get)
    }


    final def in[T: Tag](function: T => F[_, _])(implicit pos: CodePositionMaterializer): Unit = {
      take2(function, pos.get)
    }
  }

//  abstract class StringVerbBlockRegistration {
//    def apply(string: String, verb: String, pos: source.Position, block: () => Unit): Unit
//  }


  def getSimpleNameOfAnObjectsClass(o: AnyRef): String = stripDollars(parseSimpleName(o.getClass.getName))

  // [bv: this is a good example of the expression type refactor. I moved this from SuiteClassNameListCellRenderer]
  // this will be needed by the GUI classes, etc.
  def parseSimpleName(fullyQualifiedName: String): String = {

    val dotPos = fullyQualifiedName.lastIndexOf('.')

    // [bv: need to check the dotPos != fullyQualifiedName.length]
    if (dotPos != -1 && dotPos != fullyQualifiedName.length)
      fullyQualifiedName.substring(dotPos + 1)
    else
      fullyQualifiedName
  }

  // This attempts to strip dollar signs that happen when using the interpreter. It is quite fragile
  // and already broke once. In the early days, all funky dollar sign encrusted names coming out of
  // the interpreter started with "line". Now they don't, but in both cases they seemed to have at
  // least one "$iw$" in them. So now I leave the string alone unless I see a "$iw$" in it. Worst case
  // is sometimes people will get ugly strings coming out of the interpreter. -bv April 3, 2012
  def stripDollars(s: String): String = {
    val lastDollarIndex = s.lastIndexOf('$')
    if (lastDollarIndex < s.length - 1)
      if (lastDollarIndex == -1 || !s.contains("$iw$")) s else s.substring(lastDollarIndex + 1)
    else {
      // The last char is a dollar sign
      val lastNonDollarChar = s.reverse.find(_ != '$')
      lastNonDollarChar match {
        case None => s
        case Some(c) => {
          val lastNonDollarIndex = s.lastIndexOf(c.toInt)
          if (lastNonDollarIndex == -1) s
          else stripDollars(s.substring(0, lastNonDollarIndex + 1))
        }
      }
    }
  }
}
