package izumi.distage.roles.launcher

import izumi.logstage.api.logger.LogRouter

import java.util.concurrent.atomic.AtomicReference

/**
  * Per-application holder of the most-recently-initialized application `LogRouter`, used by
  * [[izumi.distage.roles.RoleAppMain.main]] to report a fatal failure through the configured (format-resolved)
  * router after the boot graph has been torn down.
  */
final class CrashLogRouterRef(ref: AtomicReference[Option[LogRouter]]) {
  def set(router: LogRouter): Unit = ref.set(Some(router))
  def get: Option[LogRouter] = ref.get()
}
