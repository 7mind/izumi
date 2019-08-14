package izumi.distage.testkit.services.st.dtest

import izumi.distage.testkit.services.st.dtest.ScalatestWords.{DSCanVerb, DSMustVerb, DSShouldVerb, DSStringVerbBlockRegistration}
import org.scalactic.source
import org.scalactic.source.Position

@org.scalatest.Finders(value = Array("org.scalatest.finders.WordSpecFinder"))
trait ScalatestWords extends DSShouldVerb with DSMustVerb with DSCanVerb  {
  protected implicit val subjectRegistrationFunction1: DSStringVerbBlockRegistration = new DSStringVerbBlockRegistration {
    def apply(left: String, verb: String, pos: source.Position, f: () => Unit): Unit = {
      registerBranch(left, Some(verb), verb, "apply", 6, -2, pos, f)
    }
  }

  protected[testkit] def registerBranch(description: String, childPrefix: Option[String], verb: String, methodName: String, stackDepth: Int, adjustment: Int, pos: source.Position, fun: () => Unit): Unit
}

object ScalatestWords {

  // shameless copypaste from ScalaTest

  abstract class DSStringVerbBlockRegistration {
    def apply(string: String, verb: String, pos: source.Position, block: () => Unit): Unit
  }

  abstract class DSStringVerbStringInvocation {
    def apply(subject: String, verb: String, predicate: String, pos: source.Position): DSResultOfStringPassedToVerb
  }

  abstract class DSResultOfStringPassedToVerb(val verb: String, val rest: String) {
    //def taggedAs(firstTestTag: Tag, otherTestTags: Tag*): ResultOfTaggedAsInvocation
  }

  final class DSResultOfAfterWordApplication(val text: String, val f: () => Unit) {
    override def toString: String = text
  }

  abstract class DSSubjectWithAfterWordRegistration {
    def apply(subject: String, verb: String, resultOfAfterWordApplication: DSResultOfAfterWordApplication, pos: source.Position): Unit
  }


  trait DSShouldVerb {

    trait DSStringShouldWrapperForVerb {

      val leftSideString: String

      val pos: source.Position

      def should(right: String)(implicit svsi: DSStringVerbStringInvocation): DSResultOfStringPassedToVerb = {
        svsi(leftSideString, "should", right, pos)
      }

      def should(right: => Unit)(implicit fun: DSStringVerbBlockRegistration): Unit = {
        fun(leftSideString, "should", pos, right _)
      }

      def should(resultOfAfterWordApplication: DSResultOfAfterWordApplication)(implicit swawr: DSSubjectWithAfterWordRegistration): Unit = {
        swawr(leftSideString, "should", resultOfAfterWordApplication, pos)
      }
    }

    import scala.language.implicitConversions

    implicit def convertToStringShouldWrapperForVerb(o: String)(implicit position: source.Position): DSStringShouldWrapperForVerb =
      new DSStringShouldWrapperForVerb {
        val leftSideString: String = o.trim
        val pos: Position = position
      }
  }


  trait DSCanVerb {

    trait DSStringCanWrapperForVerb {

      val leftSideString: String

      val pos: source.Position

      def can(right: String)(implicit svsi: DSStringVerbStringInvocation): DSResultOfStringPassedToVerb = {
        svsi(leftSideString, "can", right, pos)
      }

      def can(right: => Unit)(implicit fun: DSStringVerbBlockRegistration): Unit = {
        fun(leftSideString, "can", pos, right _)
      }

      def can(resultOfAfterWordApplication: DSResultOfAfterWordApplication)(implicit swawr: DSSubjectWithAfterWordRegistration): Unit = {
        swawr(leftSideString, "can", resultOfAfterWordApplication, pos)
      }
    }

    import scala.language.implicitConversions

    implicit def convertToStringCanWrapper(o: String)(implicit position: source.Position): DSStringCanWrapperForVerb =
      new DSStringCanWrapperForVerb {
        val leftSideString: String = o.trim
        val pos: Position = position
      }
  }

  trait DSMustVerb {

    trait DSStringMustWrapperForVerb {

      val leftSideString: String

      val pos: source.Position

      def must(right: String)(implicit svsi: DSStringVerbStringInvocation): DSResultOfStringPassedToVerb = {
        svsi(leftSideString, "must", right, pos)
      }

      def must(right: => Unit)(implicit fun: DSStringVerbBlockRegistration): Unit = {
        fun(leftSideString, "must", pos, right _)
      }

      def must(resultOfAfterWordApplication: DSResultOfAfterWordApplication)(implicit swawr: DSSubjectWithAfterWordRegistration): Unit = {
        swawr(leftSideString, "must", resultOfAfterWordApplication, pos)
      }
    }

    import scala.language.implicitConversions

    implicit def convertToStringMustWrapperForVerb(o: String)(implicit position: source.Position): DSStringMustWrapperForVerb =
      new DSStringMustWrapperForVerb {
        val leftSideString: String = o.trim
        val pos: Position = position
      }
  }


}
