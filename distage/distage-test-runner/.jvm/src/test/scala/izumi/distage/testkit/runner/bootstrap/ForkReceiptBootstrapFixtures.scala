package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.{FileForkReceiptStore, ForkReceiptArguments}

import java.nio.file.{Files, Paths}

object ForkReceiptBootstrapFixtures {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Expected a new owned fixture directory")
    val parent = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(parent), "Fixture directory must be new")
    val store = FileForkReceiptStore.create(parent)
    val other = FileForkReceiptStore.create(parent)
    try {
      val user = Vector("--build-id", "fixture-build", "--target-id", "fixture-target", "--catalogue-id", "fixture-catalogue")
      val values = user ++ Vector(ForkReceiptArguments.HostDirectoryOption, store.directory.toString)
      val remote = Vector("user-remote")
      val framework = new Framework
      val main = framework.runner(values.toArray, remote.toArray, getClass.getClassLoader)
      require(!main.isInstanceOf[ForkReceiptRunner] && main.args().toVector == values, "In-process runner changed arguments or waits for fork receipts")
      val forwarded = remote ++ Vector(ForkReceiptArguments.ForkDirectoryOption, store.directory.toString)
      require(main.remoteArgs().toVector == forwarded && main.remoteArgs().toVector == forwarded, "Fork activation is not stable or lost user remote arguments")
      val modified = main.remoteArgs(); modified(0) = "mutation"
      require(main.remoteArgs().toVector == forwarded, "Caller mutation changed activation")
      val child = framework.runner(main.args(), main.remoteArgs(), getClass.getClassLoader)
      require(child.isInstanceOf[ForkReceiptRunner] && child.remoteArgs().toVector == forwarded && child.args().toVector == values, "Fork did not use explicit activation")
      require(child.done() == "" && child.done() == "" && main.done() == "", "Empty selection did not complete without receipt delivery")
      require(ForkReceiptArguments.parse(values, remote).arguments == user, "Internal host options reached request parsing")
      rejects { val _ = ForkReceiptArguments.parse(values ++ Vector(ForkReceiptArguments.HostDirectoryOption, store.directory.toString), remote) }
      rejects { val _ = ForkReceiptArguments.parse(user :+ ForkReceiptArguments.HostDirectoryOption, remote) }
      rejects { val _ = ForkReceiptArguments.parse(user, forwarded) }
      rejects { val _ = ForkReceiptArguments.parse(values, remote ++ Vector(ForkReceiptArguments.ForkDirectoryOption, other.directory.toString)) }
      rejects { val _ = ForkReceiptArguments.parse(values ++ Vector(ForkReceiptArguments.ForkDirectoryOption, store.directory.toString), remote) }
      rejects { val _ = ForkReceiptArguments.parse(values, remote ++ Vector(ForkReceiptArguments.HostDirectoryOption, store.directory.toString)) }
      val commandValues = values :+ ForkReceiptArguments.CommandCompletionOption
      val command = ForkReceiptArguments.parse(commandValues, remote)
      require(command.commandCompletion && !command.forked && command.arguments == user, "Command completion options reached request parsing")
      val commandHost = framework.runner(commandValues.toArray, remote.toArray, getClass.getClassLoader)
      require(commandHost.args().toVector == commandValues && commandHost.remoteArgs().toVector == forwarded && commandHost.done() == "", "Command completion lost explicit fork ownership")
      val commandChild = ForkReceiptArguments.parse(commandValues, forwarded)
      require(commandChild.commandCompletion && commandChild.forked, "Fork lost command completion activation")
      rejects { val _ = framework.runner(commandValues.toArray, forwarded.toArray, getClass.getClassLoader) }
      rejects { val _ = ForkReceiptArguments.parse(commandValues :+ ForkReceiptArguments.CommandCompletionOption, remote) }
      rejects { val _ = ForkReceiptArguments.parse(user :+ ForkReceiptArguments.CommandCompletionOption, remote) }
      rejects { val _ = ForkReceiptArguments.parse(values, remote :+ ForkReceiptArguments.CommandCompletionOption) }
      println("FORK_BOOTSTRAP_CHECK_OK explicit fork activation, immutable forwarding, empty selection and invalid ownership")
      println("FORK_COMMAND_ARGUMENTS_CHECK_OK host and fork activation, missing agent, duplicate and unowned options")
    } finally { store.close(); other.close(); Files.delete(parent) }
  }

  private def rejects(body: => Unit): Unit = {
    var rejected = false
    try body catch { case _: IllegalArgumentException => rejected = true }
    require(rejected, "Invalid fork ownership was accepted")
  }
}
