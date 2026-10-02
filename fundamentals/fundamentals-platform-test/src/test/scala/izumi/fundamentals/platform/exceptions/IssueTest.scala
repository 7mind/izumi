package izumi.fundamentals.platform.exceptions

import izumi.fundamentals.platform.exceptions.Issue.IssueContext
import org.scalatest.wordspec.AnyWordSpec

class IssueTest extends AnyWordSpec {
  "issue" should {
    "preserve context" in {
      val a = IssueTest.MyIssue(1)
      assert(a.toString.contains("(1)"))
      assert(a.toString.contains("(IssueTest.scala:9)"))
    }
  }
}

object IssueTest {
  case class MyIssue(
    a: Int
  )(implicit val
    context: IssueContext
  ) extends Issue
}
