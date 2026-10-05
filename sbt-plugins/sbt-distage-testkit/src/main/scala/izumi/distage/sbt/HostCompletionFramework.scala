package izumi.distage.sbt

import izumi.distage.sbt.target.TaskCompleteness
import izumi.distage.testkit.protocol.ForkReceiptArguments

import sbt.testing.{Fingerprint, Framework, Runner, Task, TaskDef}

private[sbt] final class HostCompletionFramework(val delegate: Framework) extends Framework {
  override def name(): String = delegate.name()
  override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  override def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner = {
    val original = delegate.runner(args, remoteArgs, loader)
    ForkReceiptArguments.parse(args.toVector, remoteArgs.toVector).hostDirectory match {
      case None =>
        new Runner {
          override def args(): Array[String] = original.args()
          override def remoteArgs(): Array[String] = original.remoteArgs()
          override def done(): String = original.done()
          override def tasks(definitions: Array[TaskDef]): Array[Task] = TaskCompleteness.protect(original.tasks(definitions))
        }
      case Some(directory) =>
        val completions = new TaskCompleteness.FileCompletionStore(directory)
        new Runner {
          override def args(): Array[String] = original.args()
          override def remoteArgs(): Array[String] = original.remoteArgs()
          override def done(): String = original.done()
          override def tasks(definitions: Array[TaskDef]): Array[Task] = TaskCompleteness.normalise(definitions, original.tasks(definitions), completions)
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
