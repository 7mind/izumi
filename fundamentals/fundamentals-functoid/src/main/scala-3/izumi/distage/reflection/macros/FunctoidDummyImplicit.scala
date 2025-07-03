package izumi.distage.reflection.macros

trait FunctoidDummyImplicit

final class IgnorableFunctoidDummyImplicit private() extends FunctoidDummyImplicit
object IgnorableFunctoidDummyImplicit {
  implicit val dummyImplicit: IgnorableFunctoidDummyImplicit = new IgnorableFunctoidDummyImplicit
}

final class UnignorableDummyImplicit private () extends FunctoidDummyImplicit
object UnignorableDummyImplicit {
  implicit val unignorableDummyImplicit: UnignorableDummyImplicit = new UnignorableDummyImplicit
}

