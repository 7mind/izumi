package izumi.distage.sbt

import izumi.distage.sbt.target.{TaskCompleteness, TaskGroups}
import izumi.distage.testkit.protocol.ForkReceiptArguments

import sbt.testing.{Fingerprint, Framework, Runner, Task, TaskDef}

private[sbt] final class HostCompletionFramework(val delegate: Framework) extends Framework {
  override def name(): String = delegate.name()
  override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  override def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner = {
    val groups = TaskGroups.parse(args)
    val original = delegate.runner(groups.arguments(), remoteArgs, loader)
    def capture(tasks: Array[Task]): Array[Task] =
      if (groups.directory() == null) tasks else TaskGroups.capture(tasks, new TaskGroups.FileStore(groups.directory()))
    val completions = ForkReceiptArguments.parse(groups.arguments().toVector, remoteArgs.toVector).hostDirectory.map(new TaskCompleteness.FileCompletionStore(_))
    new Runner {
      override def args(): Array[String] = original.args()
      override def remoteArgs(): Array[String] = original.remoteArgs()
      override def done(): String = original.done()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = {
        val tasks = original.tasks(definitions)
        capture(completions.fold(TaskCompleteness.protect(tasks))(store => TaskCompleteness.normalise(definitions, tasks, store)))
      }
    }
  }
}

private[sbt] object HostCompletionFramework {
  def wrap(framework: Framework): Framework = framework match {
    case current: HostCompletionFramework => current
    case other => new HostCompletionFramework(other)
  }
}
