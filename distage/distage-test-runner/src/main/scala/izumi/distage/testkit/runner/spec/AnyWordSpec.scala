package izumi.distage.testkit.runner.spec

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.control.NonFatal

trait TestBody[A] {
  def evaluate(body: => A, executionContext: ExecutionContext): Future[Unit]
}
trait LowPriorityTestBodies {
  implicit def synchronous[A]: TestBody[A] = new TestBody[A] {
    override def evaluate(body: => A, executionContext: ExecutionContext): Future[Unit] = {
      implicit val ec: ExecutionContext = executionContext
      Future { val _ = body; () }
    }
  }
}
object TestBody extends LowPriorityTestBodies {
  implicit def asynchronous[A]: TestBody[Future[A]] = new TestBody[Future[A]] {
    override def evaluate(body: => Future[A], executionContext: ExecutionContext): Future[Unit] = {
      implicit val ec: ExecutionContext = executionContext
      body.map(_ => ())
    }
  }
}

abstract class AnyWordSpec extends TestAssertions with TestSuite {
  private final class Registration(val path: Vector[String], val location: SourceLocation, val body: ExecutionContext => Future[Unit])
  private var prefix = Vector.empty[String]
  private var registrations = Vector.empty[Registration]
  private var registered = false
  @volatile private var ownedExecutionContext = Option.empty[ExecutionContext]

  protected def suiteId: SuiteId = SuiteId(getClass.getName)
  protected def suiteName: String = getClass.getSimpleName
  protected final def sessionExecutionContext: ExecutionContext = ownedExecutionContext.getOrElse(throw new IllegalStateException("Execution context is unavailable before session registration"))

  implicit final def wordSpecString(text: String): WordSpecString = new WordSpecString(text.trim)

  final class WordSpecString(text: String) {
    infix def should(body: => Unit): Unit = branch("should", () => body)
    infix def must(body: => Unit): Unit = branch("must", () => body)
    infix def can(body: => Unit): Unit = branch("can", () => body)

    private def branch(verb: String, body: () => Unit): Unit = {
      require(!registered, "Suite registration is already frozen")
      val outer = prefix
      prefix = outer ++ Vector(text, verb)
      try body() finally { prefix = outer }
    }

    infix def in[A](body: => A)(implicit adapter: TestBody[A], position: RegistrationPosition): Unit = {
      require(!registered, "Suite registration is already frozen")
      registrations :+= new Registration(prefix :+ text, position.location, ec => adapter.evaluate(body, ec))
    }
  }

  final override def register(context: RegistrationContext): RegisteredSuite = synchronized {
    require(!registered, "Suite instance cannot be shared between sessions")
    registered = true
    ownedExecutionContext = Some(context.executionContext)
    val descriptor = SuiteDescriptor(suiteId, suiteName)
    val tests = registrations.map { registration =>
      val id = TestId(context.target, descriptor.id, registration.path, None)
      PlainRegisteredTest(TestDescriptor(id, registration.path.mkString(" "), registration.location, EffectiveSettings(Vector.empty, memoization = true)), registration.body)
    }
    val provider = context.provider(ProviderId("plain"), () => new PlainExecutionProvider(context.executionContext))
    provider.add(tests)
    RegisteredSuite(descriptor, tests.map(_.descriptor), provider)
  }
}

abstract class AsyncWordSpec extends AnyWordSpec {
  private val forwardingExecutionContext: ExecutionContext = new ExecutionContext {
    private var tail: Future[Unit] = Future.successful(())

    override def execute(runnable: Runnable): Unit = {
      val delegate = sessionExecutionContext
      val completion = Promise[Unit]()
      val previous = synchronized {
        val previous = tail
        tail = completion.future
        previous
      }
      try previous.onComplete { _ =>
        try runnable.run()
        catch { case NonFatal(cause) => delegate.reportFailure(cause) }
        finally { val _ = completion.trySuccess(()) }
      }(delegate)
      catch { case NonFatal(cause) => val _ = completion.tryFailure(cause); throw cause }
    }

    override def reportFailure(cause: Throwable): Unit = sessionExecutionContext.reportFailure(cause)
  }

  implicit def executionContext: ExecutionContext = forwardingExecutionContext
}
