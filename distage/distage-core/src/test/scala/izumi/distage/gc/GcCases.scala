package izumi.distage.gc

import distage.ModuleDef
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.language.Quirks._

@ExposedTestScope
object GcCases {

  object InjectorCase1 {

    class Circular1(val c2: Circular2)

    class Circular2(val c1: Circular1, val c4: Circular4)

    class Circular3(val c4: Circular4)

    class Circular4(val c3: Circular3)

    class Trash()

  }

  object InjectorCase2 {

    trait MkS3Client

    class S3Component(client: => MkS3Client) {
      def nothing(): Unit = {
        client.discard()
      }
    }

    class Impl(val c: S3Component) extends MkS3Client

    class App(val client: MkS3Client, val component: S3Component)

  }

  object InjectorCase3 {

    trait MkS3Client

    trait IntegrationComponent

    class Impl(val c: S3Component) extends MkS3Client

    class S3Component(val client: MkS3Client) extends IntegrationComponent

    class S3Upload(val client: MkS3Client)

    class Ctx(val upload: S3Upload, val ics: Set[IntegrationComponent])

  }

  object InjectorCase4 {

    class MkS3Client(val c: S3Component)

    trait IntegrationComponent

    class S3Component(val client: MkS3Client) extends IntegrationComponent

    class S3Upload(val client: MkS3Client)

    class Ctx(val upload: S3Upload)

    class Initiator(val components: Set[IntegrationComponent])

  }

  object InjectorCase5 {

    trait T1

    trait T2

    class Circular1(val c1: T1, val c2: T2) extends T1

    class Circular2(val c1: T1, val c2: T2) extends T2

  }

  object InjectorCase6 {

    trait Circular1 {
      def nothing: Int

      def c1: Circular1

      def c2: Circular2
    }

    trait Circular2 {
      def nothing: Int

      def c1: Circular1

      def c2: Circular2
    }

  }

  object InjectorCase7 {

    class Circular1(bnc1: => Circular1, bnc2: => Circular2, val c1: Circular1, val c2: Circular2) {
      def nothing(): Unit = {
        bnc1.discard()
        bnc2.discard()
      }
    }

    class Circular2(bnc1: => Circular1, bnc2: => Circular2) {
      def nothing(): Unit = {
        bnc1.discard()
        bnc2.discard()
      }
    }

  }

  object InjectorCase8 {

    trait Component

    class TestComponent() extends Component with AutoCloseable {
      override def close(): Unit = {}
    }

    class App(val components: Set[Component], val closeables: Set[AutoCloseable])

  }

  object InjectorCase9 {

    trait T1

    trait T2

    final class Circular1(val c1: T1, val c2: T2) extends T1

    final class Circular2(val c1: T1, val c2: T2) extends T2

  }

  object InjectorCase10 {

    final class Circular1(val c1: Circular1, val c2: Circular2)

    final class Circular2(val c1: Circular1, val c2: Circular2)

  }

  object InjectorCase11 {

    class Circular1(val c1: Circular1, val c2: Circular2)

    final class Circular2(val c1: Circular1)

  }

  object InjectorCase12 {

    trait T1 extends AutoCloseable {
      override def close(): Unit = {}
    }

    class Circular1(c1: => Circular1, c2: => Circular2) extends T1 {
      def nothing(): Unit = {
        c1.discard()
        c2.discard()
      }
    }

    class Circular2(c1: => Circular1, c2: => Circular2) extends T1 {
      def nothing(): Unit = {
        c1.discard()
        c2.discard()
      }
    }

    class Circular3(c1: => Circular1, val c2: Circular2) {
      def nothing(): Unit = {
        c1.discard()
      }
    }

    class Circular4(c1: => Circular1) {
      def nothing(): Unit = {
        c1.discard()
      }
    }

  }

  object InjectorCase13 {
    final class T1()
    final class Box[A](private val a: A) extends AnyVal
    final class Circular1(c1: => Circular1, c2: => Circular2, val q: Box[T1]) {
      def nothing(): Unit = {
        c1.discard()
        c2.discard()
      }
    }

    final class Circular2(c1: => Circular1, c2: => Circular2, val q: Box[T1]) {
      def nothing(): Unit = {
        c1.discard()
        c2.discard()
      }
    }
  }

  object InjectorCase14_GC {
    sealed trait Elem

    final case class Strong() extends Elem

    final case class Weak() extends Elem

    final val module = new ModuleDef {
      make[Strong]
      make[Weak]

      many[Elem]
        .ref[Strong]
        .weak[Weak]
    }
  }

}
