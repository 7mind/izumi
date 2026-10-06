package izumi.distage.sbt

import sbt.{ExecuteProgress2, ExecuteProgressAdapter, Result, State, TaskId}

private[sbt] final class HostCommandCompletion(inherited: ExecuteProgress2, owners: Seq[HostReceiptOwner]) extends ExecuteProgressAdapter(inherited) {
  private var activeWork = 0

  override def beforeCommand(command: String, state: State): Unit = inherited.beforeCommand(command, state)

  override def beforeWork(task: TaskId[?]): Unit = {
    synchronized { activeWork += 1 }
    try inherited.beforeWork(task)
    catch { case cause: Throwable => finishWork(); throw cause }
  }

  override def afterWork[A](task: TaskId[A], result: Either[TaskId[A], Result[A]]): Unit = {
    try {
      result match {
        case Right(Result.Inc(failure)) if failure.directCause.exists(_.isInstanceOf[InterruptedException]) => owners.foreach(_.cancelForks())
        case _ => ()
      }
      inherited.afterWork(task, result)
    }
    finally {
      finishWork()
      // SBT shuts the pool down again after the first worker returns; siblings must finish reporting first.
      synchronized { while (activeWork > 0 && owners.exists(_.hasInterruption)) wait() }
    }
  }

  private def finishWork(): Unit = synchronized {
    activeWork -= 1
    require(activeWork >= 0, "Command task work count became negative")
    notifyAll()
  }

  override def afterCommand(command: String, result: Either[Throwable, State]): Unit = {
    var original = result.left.toOption
    try {
      if (owners.exists(_.hasPending)) synchronized { while (activeWork > 0) wait() }
      inherited.afterCommand(command, result)
    }
    catch { case cause: Throwable => original = Some(cause); throw cause }
    finally {
      var cleanupFailure = Option.empty[Throwable]
      owners.foreach { owner =>
        try owner.finishCommand()
        catch {
          case cause: Throwable => cleanupFailure match {
            case Some(previous) => previous.addSuppressed(cause)
            case None => cleanupFailure = Some(cause)
          }
        }
      }
      cleanupFailure.foreach { cause =>
        original match {
          case Some(previous) => previous.addSuppressed(cause)
          case None => throw cause
        }
      }
    }
  }
}
