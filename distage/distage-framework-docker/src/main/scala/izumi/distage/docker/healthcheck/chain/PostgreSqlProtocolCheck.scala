package izumi.distage.docker.healthcheck.chain

import java.net.{InetSocketAddress, Socket}
import java.nio.ByteBuffer

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.logstage.api.IzLogger
import org.apache.commons.codec.binary.Hex

final class PostgreSqlProtocolCheck[Tag](
  userName: String = "postgres",
  databaseName: String = "postgres",
  port: DockerPort = DockerPort.TCP(5432),
) extends ChainedContainerHealthCheck[Tag] {
  override def check(
    logger: IzLogger,
    container: DockerContainer[Tag],
    portStatus: HealthCheckResult.AvailableOnPorts,
  ): ContainerHealthCheck.HealthCheckResult = {
    portStatus.availablePorts.availablePorts.get(port) match {
      case Some(value) if portStatus.requiredPortsAccessible =>
        val startupMessage = genStartupMessage()
        val socket = new Socket()
        try {
          val availablePort = value.head
          socket.connect(new InetSocketAddress(availablePort.hostV4, availablePort.port), 3000)
          logger.info(s"Checking PostgreSQL protocol on $port. Startup message: ${Hex.encodeHexString(startupMessage)}.")
          val out = socket.getOutputStream
          val in = socket.getInputStream
          out.write(startupMessage)
          val messageType = new String(in.readNBytes(1))
          if (messageType == "R") {
            logger.info(s"PostgreSQL protocol on $port is available.")
            ContainerHealthCheck.HealthCheckResult.Available
          } else {
            logger.warn(s"PostgreSQL protocol on $port unavailable due to unknown message type: $messageType.")
            ContainerHealthCheck.HealthCheckResult.Unavailable
          }
        } catch {
          case t: Throwable =>
            logger.warn(s"PostgreSQL protocol on $port unavailable due to unexpected exception. ${t.getMessage -> "Failure"}")
            ContainerHealthCheck.HealthCheckResult.Unavailable
        } finally {
          socket.close()
        }
      case _ => ContainerHealthCheck.HealthCheckResult.Ignored
    }
  }

  private def genStartupMessage(): Array[Byte] = {
    def write(buffer: ByteBuffer, message: String): Unit = {
      val currentPos = buffer.position()
      buffer.put(message.getBytes())
      buffer.position(currentPos + message.length)
      buffer.put(0.toByte)
      buffer.position(currentPos + message.length + 1)
    }
    val size = 4 + 4 + 4 + 1 + userName.length + 1 + 8 + 1 + databaseName.length + 1 + 1
    val buffer = ByteBuffer.allocate(size)
    buffer.position(0).putInt(size) // size
    buffer.position(4).putInt(196608) // protocol
    write(buffer, "user")
    write(buffer, userName)
    write(buffer, "database")
    write(buffer, databaseName)
    buffer.put(0.toByte)
    buffer.array()
  }
}
