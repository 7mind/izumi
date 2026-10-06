package izumi.distage.sbt

import sbt.*
import sbt.Keys.{configuration, thisProject}

private[sbt] object HostTaskBoundary {
  def input(key: InputKey[TestResult], owner: SettingKey[HostReceiptOwner]): Def.Setting[InputTask[TestResult]] = {
    key.set0(Def.setting {
      val inherited = original(key, owner).value
      val receiptOwner = owner.value
      inherited.mapTask(task => receiptOwner.input(task))
    }, NoPosition)
  }

  private def original(key: InputKey[TestResult], owner: SettingKey[HostReceiptOwner]): Def.Initialize[InputTask[TestResult]] = Def.settingDyn {
    val config = ConfigKey(configuration.value.name)
    val definitions = thisProject.value.settings.filter(setting => setting.key.key == key.key && setting.key.scope.config == ScopeAxis.Select(config))
    val boundaries = definitions.zipWithIndex.filter { case (setting, _) => setting.init.dependencies.exists(_.key == owner.key) }
    require(boundaries.size == 1, "Distage input boundary must be unique within its configuration")
    val originals = definitions.take(boundaries.head._2).map { setting =>
      setting.mapReferenced([a] => (reference: Def.ScopedKey[a]) => {
        if (reference.key == Keys.testResultLogger.key) Def.ScopedKey(reference.scope, HostReceiptPolicy.normalisedLogger.key).asInstanceOf[Def.ScopedKey[a]]
        else if (reference.key == Keys.testFilter.key) Def.ScopedKey(reference.scope, HostReceiptPolicy.normalisedFilter.key).asInstanceOf[Def.ScopedKey[a]]
        else reference
      }).asInstanceOf[Def.Setting[InputTask[TestResult]]]
    }
    require(originals.nonEmpty, "Distage input boundary requires an inherited input definition")
    originals.tail.foldLeft(originals.head.init) { (previous, definition) =>
      if (definition.definitive) definition.init else Def.settingDyn {
        val inherited = previous.value
        definition.mapConstant([a] => (reference: Def.ScopedKey[a]) => {
          if (reference == definition.key) Some(inherited.asInstanceOf[a]) else None
        }).init
      }
    }
  }

  def output(key: TaskKey[Tests.Output], owner: SettingKey[HostReceiptOwner]): Def.Setting[Task[Tests.Output]] = key.toSettingKey := {
    val inherited = key.taskValue
    owner.value.output(inherited)
  }
}
