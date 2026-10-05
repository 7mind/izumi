package izumi.distage.sbt

import izumi.distage.testkit.protocol.ForkCompletionAgent

import sbt.{MessageOnlyException, Tests}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.{Base64, UUID}
import java.util.jar.{Attributes, JarEntry, JarOutputStream, Manifest}
import java.util.concurrent.TimeUnit

private[sbt] final class HostForkCompletion(directory: Path) {
  private final val ExitWaitSeconds = 30L
  private final val PollMillis = 5L
  private val agent = directory.resolve("distage-fork-agent.jar")
  private var forks = Vector.empty[Path]

  def group(value: Tests.Group): Tests.Group = synchronized {
    value.runPolicy match {
      case Tests.InProcess => value
      case Tests.SubProcess(options) =>
        if (!Files.isRegularFile(agent)) packageAgent()
        val prefix = directory.resolve("fork-" + UUID.randomUUID().toString)
        forks :+= prefix
        val encoded = Base64.getUrlEncoder.withoutPadding().encodeToString(prefix.toString.getBytes(StandardCharsets.UTF_8))
        val argument = "-javaagent:" + agent + "=" + encoded + ":" + ProcessHandle.current().pid()
        new Tests.Group(value.name, value.tests, Tests.SubProcess(options.withRunJVMOptions(argument +: options.runJVMOptions)), value.tags)
    }
  }

  def finish(commit: Boolean): Unit = {
    val admitted = synchronized { forks.filter(prefix => Files.isRegularFile(path(prefix, "entered"))) }
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
    val name = classOf[ForkCompletionAgent].getName.replace('.', '/') + ".class"
    val source = classOf[ForkCompletionAgent].getResourceAsStream("ForkCompletionAgent.class")
    require(source != null, "Fork completion agent bytecode is missing")
    try {
      val output = new JarOutputStream(Files.newOutputStream(agent), manifest)
      try {
        output.putNextEntry(new JarEntry(name))
        val _ = source.transferTo(output)
        output.closeEntry()
      } finally output.close()
    } finally source.close()
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
