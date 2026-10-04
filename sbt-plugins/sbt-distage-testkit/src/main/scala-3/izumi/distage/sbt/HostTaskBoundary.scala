package izumi.distage.sbt

import sbt.*

private[sbt] object HostTaskBoundary {
  def input[A](key: InputKey[A], owner: SettingKey[HostReceiptOwner]): Def.Setting[InputTask[A]] = key.set0(Def.setting {
    val inherited = key.value
    val receiptOwner = owner.value
    inherited.mapTask(task => receiptOwner.input(task))
  }, NoPosition)

  def output(key: TaskKey[Tests.Output], owner: SettingKey[HostReceiptOwner]): Def.Setting[Task[Tests.Output]] = key.toSettingKey := {
    val inherited = key.taskValue
    owner.value.output(inherited)
  }
}
