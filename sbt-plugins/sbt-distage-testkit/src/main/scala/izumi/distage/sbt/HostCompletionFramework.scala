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
    ForkReceiptArguments.parse(groups.arguments().toVector, remoteArgs.toVector).hostDirectory match {
      case None =>
        new Runner {
          override def args(): Array[String] = original.args()
          override def remoteArgs(): Array[String] = original.remoteArgs()
          override def done(): String = original.done()
          override def tasks(definitions: Array[TaskDef]): Array[Task] = capture(TaskCompleteness.protect(original.tasks(definitions)))
        }
      case Some(directory) =>
        val completions = new TaskCompleteness.FileCompletionStore(directory)
        new Runner {
          override def args(): Array[String] = original.args()
          override def remoteArgs(): Array[String] = original.remoteArgs()
          override def done(): String = original.done()
          override def tasks(definitions: Array[TaskDef]): Array[Task] = capture(TaskCompleteness.normalise(definitions, original.tasks(definitions), completions))
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
