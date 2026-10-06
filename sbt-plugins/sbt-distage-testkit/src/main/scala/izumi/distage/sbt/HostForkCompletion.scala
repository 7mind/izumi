package izumi.distage.sbt

import izumi.distage.sbt.target.{ForeignRunReports, ForkCompletionAgent, TaskCompleteness}
import izumi.distage.testkit.protocol.ForkProcessId
import net.bytebuddy.ByteBuddy

import sbt.{MessageOnlyException, Tests}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.{Base64, UUID}
import java.util.jar.{Attributes, JarEntry, JarOutputStream, Manifest}
import java.util.concurrent.TimeUnit

private[sbt] final case class HostForkAdmission(process: ForkProcessId, suites: Set[HostSuiteName])

private[sbt] final class HostForkCompletion(directory: Path) {
  private final val ExitWaitSeconds = 30L
  private final val PollMillis = 5L
  private val agent = directory.resolve("distage-fork-agent.jar")
  private var forks = Vector.empty[(Path, Set[HostSuiteName])]

  def group(value: Tests.Group): Tests.Group = synchronized {
    value.runPolicy match {
      case Tests.InProcess => value
      case Tests.SubProcess(options) =>
        if (!Files.isRegularFile(agent)) packageAgent()
        val prefix = directory.resolve("fork-" + UUID.randomUUID().toString)
        forks :+= prefix -> value.tests.map(test => HostSuiteName(test.name)).toSet
        val encoded = Base64.getUrlEncoder.withoutPadding().encodeToString(prefix.toString.getBytes(StandardCharsets.UTF_8))
        val argument = "-javaagent:" + agent + "=" + encoded + ":" + ProcessHandle.current().pid()
        new Tests.Group(value.name, value.tests, Tests.SubProcess(options.withRunJVMOptions(argument +: options.runJVMOptions)), value.tags)
    }
  }

  def cancel(): Vector[HostForkAdmission] = synchronized {
    if (forks.nonEmpty) {
      val signal = directory.resolve("cancel")
      if (!Files.isRegularFile(signal)) publish(signal, "cancel")
      require(read(signal) == "cancel", "Invalid fork cancellation signal")
    }
    forks.collect {
      case (prefix, names) if names.nonEmpty && Files.isRegularFile(path(prefix, "entered")) => HostForkAdmission(ForkProcessId(read(path(prefix, "entered")).toLong), names)
    }
  }

  def finish(commit: Boolean): Unit = {
    val admitted = synchronized { forks.map(_._1).filter(prefix => Files.isRegularFile(path(prefix, "entered"))) }
    admitted.foreach { prefix =>
      val decision = path(prefix, "decision")
      if (!Files.isRegularFile(decision)) publish(decision, if (commit) "commit" else "abort")
    }
    var failure = Option.empty[Throwable]
    admitted.foreach { prefix =>
      try awaitExit(prefix)
      catch {
        case cause: Throwable => failure match {
          case Some(previous) => previous.addSuppressed(cause)
          case None => failure = Some(cause)
        }
      }
    }
    failure.foreach(cause => throw cause)
  }

  def awaitShutdown(admissions: Vector[HostForkAdmission]): Unit = {
    val admitted = synchronized { forks.map(_._1).filter(prefix => Files.isRegularFile(path(prefix, "entered")) && admissions.exists(_.process.value.toString == read(path(prefix, "entered")))) }
    admitted.foreach { prefix =>
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ExitWaitSeconds)
      while (!Files.isRegularFile(path(prefix, "shutdown")) && !Files.isRegularFile(path(prefix, "failed")) && System.nanoTime() < deadline) {
        try Thread.sleep(PollMillis)
        catch { case _: InterruptedException => val _ = Thread.interrupted() }
      }
      require(Files.isRegularFile(path(prefix, "shutdown")) && !Files.isRegularFile(path(prefix, "failed")) && read(path(prefix, "exit")) == "0\ttrue", "Fork cancellation did not complete its worker tasks: " + prefix)
    }
  }

  private def awaitExit(prefix: Path): Unit = {
    val pid = read(path(prefix, "entered")).toLong
    val process = ProcessHandle.of(pid)
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ExitWaitSeconds)
    val decision = read(path(prefix, "decision"))
    require(decision == "commit" || decision == "abort", "Invalid fork completion decision")
    val acknowledgement = path(prefix, if (decision == "commit") "ready" else "aborted")
    while (!Files.isRegularFile(acknowledgement) && !Files.isRegularFile(path(prefix, "failed")) && process.isPresent && process.get().isAlive && System.nanoTime() < deadline) Thread.sleep(PollMillis)
    if (Files.isRegularFile(path(prefix, "failed")) || !Files.isRegularFile(acknowledgement)) {
      throw new MessageOnlyException("Incomplete distage fork command acknowledgement: pid=" + pid + " prefix=" + prefix)
    }
    require(read(acknowledgement) == pid.toString && read(path(prefix, "shutdown")) == pid.toString, "Fork acknowledgement differs from its admitted process")
    if (process.isPresent) { val _ = process.get().onExit().get(ExitWaitSeconds, TimeUnit.SECONDS) }
  }

  private def packageAgent(): Unit = {
    val manifest = new Manifest
    val _ = manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    val _ = manifest.getMainAttributes.putValue("Premain-Class", classOf[ForkCompletionAgent].getName)
    val _ = manifest.getMainAttributes.putValue("Can-Retransform-Classes", "true")
    val byteBuddy = classOf[ByteBuddy].getProtectionDomain.getCodeSource.getLocation.toURI
    require(Files.isRegularFile(java.nio.file.Paths.get(byteBuddy)), "Fork exit capture dependency is not a JAR")
    val testInterface = classOf[sbt.testing.Task].getProtectionDomain.getCodeSource.getLocation.toURI
    require(Files.isRegularFile(java.nio.file.Paths.get(testInterface)), "Fork test interface dependency is not a JAR")
    val _ = manifest.getMainAttributes.put(Attributes.Name.CLASS_PATH, byteBuddy.toASCIIString + " " + testInterface.toASCIIString)
    val output = new JarOutputStream(Files.newOutputStream(agent), manifest)
    try {
      Vector(classOf[ForkCompletionAgent], classOf[TaskCompleteness], classOf[ForeignRunReports]).flatMap(value => value +: value.getDeclaredClasses.toVector).foreach { agentClass =>
        val name = agentClass.getName.replace('.', '/') + ".class"
        val source = agentClass.getResourceAsStream("/" + name)
        require(source != null, "Fork completion agent bytecode is missing: " + name)
        try {
          output.putNextEntry(new JarEntry(name))
          val _ = source.transferTo(output)
          output.closeEntry()
        } finally source.close()
      }
    } finally output.close()
  }

  private def path(prefix: Path, suffix: String): Path = prefix.resolveSibling(prefix.getFileName.toString + "." + suffix)
  private def read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  private def publish(path: Path, value: String): Unit = {
    val temporary = Files.createTempFile(directory, "fork-decision-", ".tmp")
    try {
      val _ = Files.write(temporary, value.getBytes(StandardCharsets.UTF_8))
      val _ = Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
    } finally { val _ = Files.deleteIfExists(temporary) }
  }
}
