package izumi.distage.testkit.distagesuite.memoized

object MemoizationEnv {
  final case class MemoizedInstance(uuid: UUID)
  final case class TestInstance(uuid: UUID)
  final val anotherTestInstance: TestInstance = TestInstance(UUID.randomUUID())
  final val memoizedInstance: mutable.HashSet[MemoizedInstance] = mutable.HashSet.empty

  final case class MemoizedLevel1(UUID: UUID)
  final val memoizedLevel1: mutable.HashSet[MemoizedLevel1] = mutable.HashSet.empty

  final case class MemoizedLevel2(UUID: UUID)
  final val memoizedLevel2: mutable.HashSet[MemoizedLevel2] = mutable.HashSet.empty

  final case class MemoizedLevel3(UUID: UUID)
  final val memoizedLevel3: mutable.HashSet[MemoizedLevel3] = mutable.HashSet.empty

  def makeInstance[T](set: mutable.HashSet[T])(ctor: UUID => T): T = {
    val instance = ctor(UUID.randomUUID())
    set.synchronized {
      set += instance
    }
    instance
  }
}
