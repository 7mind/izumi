package izumi.distage.testkit.runner.di

import izumi.distage.testkit.runner.CompletionAwaiter

private[di] trait RuntimeExecutionPlatformSpecific[A] { self: RuntimeExecution[A] =>
  final def awaitCompletion(): A =
    CompletionAwaiter.await(completion, () => { val _ = stop(); () }, "Multiple failures while awaiting test runtime completion")
}
