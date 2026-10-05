package izumi.distage.sbt

import sbt.*

private[sbt] object HostTaskBoundary {
  def input(key: InputKey[TestResult], owner: SettingKey[HostReceiptOwner]): Def.Setting[InputTask[TestResult]] = {
    val definitions = Defaults.testTasks.filter(_.key.key == key.key)
    require(definitions.size == 1, "Standard test input definition must be unique")
    val definition = definitions.head.mapReferenced([a] => (reference: Def.ScopedKey[a]) => {
      if (reference.key == Keys.testResultLogger.key) Def.ScopedKey(reference.scope, HostReceiptPolicy.normalisedLogger.key).asInstanceOf[Def.ScopedKey[a]]
      else reference
    }).asInstanceOf[Def.Setting[InputTask[TestResult]]]
    key.set0(Def.setting {
      val inherited = definition.init.value
      val receiptOwner = owner.value
      inherited.mapTask(task => receiptOwner.input(task))
    }, NoPosition)
  }

  def output(key: TaskKey[Tests.Output], owner: SettingKey[HostReceiptOwner]): Def.Setting[Task[Tests.Output]] = key.toSettingKey := {
    val inherited = key.taskValue
    owner.value.output(inherited)
  }
}
