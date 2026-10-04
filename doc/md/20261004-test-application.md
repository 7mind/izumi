# Test application commands

`TestApplication` owns a fresh `RunSession` for one `RunId` and explicit
catalogue identity. Supply suite factories, an execution context and a
`ProtocolOutput`; constructing the application does not instantiate suites.
The execution context belongs to its caller.

Send schema-4 `Discover`, `Request` or `Cancel` messages through `accept`.
Discovery checks the build target. Requests revalidate catalogue identity and
selection through `RunSession`, including saved requests, unknown logical IDs,
activation choices and an explicit selection matching nothing. Responses are
`Discovered`, `Resolved`, `Planned`, `Completed` or a correlated `Rejected`.
Rejected commands retain their failure phase; rejection is not a successful
execution outcome. A malformed empty run ID or a response supplied as input
fails the returned Future because it cannot be accepted as an application
command.

Non-cancellation commands execute in submission order. A resolution is cached
for its exact request. Once planning starts, the request is fixed: repeated
inspection returns the retained prepared plan or its original failure, and
execution uses that plan. A changed request is rejected. Execution consumes
the application; repeating it is rejected. Use a fresh application for another
run. Discovery can still return its retained catalogue. Descriptions contain
portable data rather than executable plans or resource instances.

Valid cancellation bypasses the command queue and requests session cancellation
immediately. It does not complete the execution Future until finalization has
finished. Run events are streamed before the terminal `Completed` response.
Cancellation admission closes before delivering the terminal Finished event;
requests submitted from that output callback are rejected. It cannot rewrite
the already constructed terminal outcome.

Output delivery is serialized per application. The first `NonFatal` delivery exception is
retained and the failed output receives no further writes. During execution,
an event-delivery failure requests cancellation and returns through the
provider's normal cleanup path; the command Future then fails with that original
exception after execution/finalization has finished. Descriptive delivery
failures fail their command immediately. Later queued commands retain the
failed Future and do not retry the output. A client must treat that failure as
an incomplete transport, even if it received earlier events. It must not infer
success from EOF or a partial result set.

`FramedProtocolOutput` encodes messages through the portable codec and writes
them to an explicit `ProtocolFrameSink`. The caller owns the sink's lifetime;
the output adapter does not close it. A `ProtocolFrameSource` reads complete
frames and reports clean EOF separately from a terminal application outcome.

On JVM, `FileProtocolFrameSink.createNew(path)` creates a dedicated UTF-8
channel, writes each frame followed by LF, and flushes each message. It refuses
to overwrite an existing file. The source returned by
`FileProtocolFrameSource.open(path)` rejects malformed UTF-8, CR delimiters, empty
frames, oversized frames and EOF inside a frame during `readFrame`. Reading is
bounded by the protocol's character limit. Frames contain
valid UTF-16 text; the codec escapes lone surrogates and non-ASCII payload
characters before UTF-8 transport. During `writeFrame`, the file adapter retains a `NonFatal` I/O failure.
During `readFrame`, it retains a `NonFatal` I/O or framing failure. Subsequent
frame operations cannot silently resume after a partial frame. Closing a channel is idempotent and later operations fail.

Test output still goes to ordinary stdout. Protocol clients read their explicit
channel, decode each complete frame and require the terminal application
outcome before reporting success. A standalone launcher and host adapters are
still pending; those clients must use this same command and selection contract.
