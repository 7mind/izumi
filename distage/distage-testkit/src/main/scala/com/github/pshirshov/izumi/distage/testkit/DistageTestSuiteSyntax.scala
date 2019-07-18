package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.testkit.DistageTestRunner.{DistageTest, TestId, TestMeta}
import com.github.pshirshov.izumi.distage.testkit.services.DISyntax
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import distage.{SafeType, Tag, TagK}
import org.scalactic.source
import org.scalatest.words.StringVerbBlockRegistration

import scala.collection.mutable
import scala.language.implicitConversions

trait AbstractDistageSpec[F[_]] {
  implicit def tagMonoIO: TagK[F]
}

object DistageTestsRegistrySingleton {
  private type Fake[T] = T
  private val registry = new mutable.HashMap[SafeType, mutable.ArrayBuffer[DistageTest[Fake]]]()

  def list[F[_]: TagK]: Seq[DistageTest[F]] = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).map(_.asInstanceOf[DistageTest[F]])
  }

  def register[F[_]: TagK](t: DistageTest[F]): Unit = synchronized {
    registry.getOrElseUpdate(SafeType.getK[F], mutable.ArrayBuffer.empty).append(t.asInstanceOf[DistageTest[Fake]])
  }

  val firstRun = new AtomicBoolean(true)
}

import org.scalatest.words.{CanVerb, MustVerb, ShouldVerb}

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait ScalatestWords extends ShouldVerb with MustVerb with CanVerb {

}


trait DistageTestSuiteSyntax[F[_]] extends AbstractDistageSpec[F] with ScalatestWords {

  protected lazy val tenv = new DistageTestEnvironmentProviderImpl()
  protected lazy val logger: IzLogger = IzLogger.apply(Log.Level.Debug)("phase" -> "test")
  protected lazy val env: TestEnvironment = tenv.loadEnvironment(logger)

  //val registeredTests: ArrayBuffer[DistageTest[F]] = mutable.ArrayBuffer[DistageTest[F]]()

  protected def distageSuiteName: String = getSimpleNameOfAnObjectsClass(this)

  protected def distageSuiteId: String = this.getClass.getName

  protected final class WordSpecStringWrapper(string: String) extends DISyntax[F] {

    override implicit def tagMonoIO: TagK[F] = DistageTestSuiteSyntax.this.tagMonoIO

    override def dio(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = {
      val id = TestId(
        Seq(left, verb, string).mkString(" "),
        distageSuiteName,
        distageSuiteId,
        distageSuiteName,
      )
      DistageTestsRegistrySingleton.register[F](DistageTest(function, env, TestMeta(id, pos.get)))
    }

    def in(function: ProviderMagnet[Any])(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = di(function)(pos)

    def in(function: ProviderMagnet[F[_]])(implicit pos: CodePositionMaterializer): Unit = dio(function)(pos)

    def in[T: Tag](function: T => F[_])(implicit pos: CodePositionMaterializer): Unit = dio(function)(implicitly[Tag[T]], pos)

    def in[T: Tag](function: T => Any)(implicit pos: CodePositionMaterializer, dummyImplicit: DummyImplicit): Unit = di(function)(implicitly[Tag[T]], pos)

  }

  private var left: String = ""
  private var verb: String = ""
  protected implicit val subjectRegistrationFunction: StringVerbBlockRegistration = new StringVerbBlockRegistration {
    def apply(left: String, verb: String, pos: source.Position, f: () => Unit): Unit = registerBranch(left, Some(verb), verb, "apply", 6, -2, pos, f)
  }

  private def registerBranch(description: String, childPrefix: Option[String], verb: String, methodName: String, stackDepth: Int, adjustment: Int, pos: source.Position, fun: () => Unit): Unit = {
    Quirks.discard(childPrefix, methodName, stackDepth, adjustment, pos)
    this.left = description
    this.verb = verb
    fun()
  }

  protected implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper = new WordSpecStringWrapper(s)

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
          val lastNonDollarIndex = s.lastIndexOf(c)
          if (lastNonDollarIndex == -1) s
          else stripDollars(s.substring(0, lastNonDollarIndex + 1))
        }
      }
    }
  }

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
}


