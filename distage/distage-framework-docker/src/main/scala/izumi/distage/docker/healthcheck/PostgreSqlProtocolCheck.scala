package izumi.distage.docker.healthcheck

import java.net.{InetSocketAddress, Socket}
import java.nio.ByteBuffer

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

final class PostgreSqlProtocolCheck[Tag](
  portStatus: HealthCheckResult.AvailableOnPorts,
  port: DockerPort = DockerPort.TCP(5432),
  userName: String = "postgres",
  databaseName: String = "postgres",
) extends ContainerHealthCheck[Tag] {
  override def check(logger: IzLogger, container: DockerContainer[Tag]): ContainerHealthCheck.HealthCheckResult = {
    portStatus.availablePorts.availablePorts.get(port) match {
      case Some(value) if portStatus.requiredPortsAccessible =>
        val startupMessage = genStartupMessage()
        val socket = new Socket()
        try {
          val availablePort = value.head
          socket.connect(new InetSocketAddress(availablePort.hostV4, availablePort.port), container.containerConfig.portProbeTimeout.toMillis.toInt)
          logger.info(s"Checking PostgreSQL protocol on $port for $container. ${startupMessage.toIterable.toHex -> "Startup message"}.")
          val out = socket.getOutputStream
          val in = socket.getInputStream
          out.write(startupMessage)
          val messageType = new String(in.readNBytes(1))
          // first byte of response message should be `R` char
          // every authentication message from PostgreSQL starts with `R`
          if (messageType == "R") {
            logger.info(s"PostgreSQL protocol on $port is available.")
            ContainerHealthCheck.HealthCheckResult.Available
          } else {
            logger.debug(s"PostgreSQL protocol on $port unavailable due to unknown message type: $messageType.")
            ContainerHealthCheck.HealthCheckResult.Unavailable
          }
        } catch {
          case t: Throwable =>
            logger.debug(s"PostgreSQL protocol on $port unavailable due to unexpected exception. ${t.getMessage -> "Failure"}")
            ContainerHealthCheck.HealthCheckResult.Unavailable
        } finally {
          socket.close()
        }
      case _ => ContainerHealthCheck.HealthCheckResult.Unavailable
    }
  }

  /** According to PostgreSQL's message flow docs every single session starts with StartUp message from front end.
    * This means that after TCP connection, front end sends to the back end message with username,
    * protocol version and database name to connect to. Back end will always respond to StartUp message
    * with authentication message type (even if user or database does not exists).
    * We are using such behavior to make sure that DB is ready to accept connections without any JDBC drivers.
    *
    * You may find more about PostgreSQL messaging flow by the links below:
    * https://www.postgresql.org/docs/9.5/protocol-flow.html
    * https://www.postgresql.org/docs/9.5/protocol-message-types.html
    * https://www.postgresql.org/docs/9.5/protocol-message-formats.html
    */
  private def genStartupMessage(): Array[Byte] = {
    def write(buffer: ByteBuffer, message: String): ByteBuffer = {
      val currentPos = buffer.position()
      buffer.put(message.getBytes())
      buffer.position(currentPos + message.length)
      buffer.put(0.toByte)
      buffer.position(currentPos + message.length + 1)
    }
    // <size int32> <protocol int32> <user const str> <0> <username str> <0> <database const str> <0> <database str> <0> <0>
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
