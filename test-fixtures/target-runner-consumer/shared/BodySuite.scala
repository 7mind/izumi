package candidate
import scala.concurrent.Future
abstract class BodySuite(index: Int) extends TargetSuite {
  "equal display name" should {
    "first" in { println("TARGET_BODY suite=" + getClass.getName + " test=1 marker=" + index); () }
    "second" in { println("TARGET_BODY suite=" + getClass.getName + " test=2 marker=" + index); () }
    "third" in { Platform.held(() => { println("TARGET_BODY suite=" + getClass.getName + " test=3 marker=" + index); () }, getClass.getName) }
  }
}
