# Portable test protocol

`distage-test-protocol` contains immutable data and JSON codecs, with no izumi
dependency. Its Scala 3 producer is 3.8.4 on JVM, JS and Native, so the SBT 2
plugin can consume it. The testkit producer remains 3.9.0. Scala 2 producers are
2.12.21 and 2.13.18. JavaScript IR and Native NIR are present in their respective
artifacts.

Schema 3 frames are compact JSON objects with `schemaVersion` and `message`.
The caller sends each frame as one line on a dedicated protocol channel.
Ordinary test output belongs on stdout, separately from that channel. JSON
escaping keeps embedded line breaks in diagnostic text within one frame.
Channel implementations and application operation bindings are separate runner
work; a codec alone does not supply a transport.

Build, target, catalogue, suite and run identities have separate types. A test
identity retains its target, suite, path vector and optional explicit variant.
Display names and source positions are separate fields and do not establish
identity. A saved request carries its catalogue identity for application-level
revalidation. The schema also carries suite settings, run overrides, structured
failure phases, causes, suppressed exceptions, field capture errors, assertion
observations, results and correlated events.
It never carries a live closure, locator or effect value.

Known source locations have a path, zero-based line and optional zero-based
UTF-16 column. Missing columns and unavailable locations are explicit. The
application determines how the path resolves against the target's source roots.

Assertion diagnostics retain a relative, absolute or virtual source identity,
an expression span and the exact compiled expression text when available.
Offsets, lines and UTF-16 columns are zero-based; ranges have an exclusive end.
Point positions, unavailable positions and unavailable compiled text remain
distinct. Source validation reports matching, mismatching, unavailable source,
unavailable range or a source-provider failure. Each observation retains its
span, compiled text, recognized kind and evaluated, skipped or rendering-failed
value. Rendering and provider failures carry their exception class and an explicit
message value. A null message is unavailable; a nonfatal exception from the
message accessor records that accessor failure's class without replacing the
original assertion. Such reporting failures are not converted to empty messages.
Schemas 1 and 2 are rejected explicitly.

Generic failures retain their original phase and exception class. A nonfatal
exception from getMessage, getCause or getStackTrace makes that field unavailable
and records the field and accessor exception class in captureErrors. Unavailable
message/stack/cause fields use an empty string/vector/vector respectively; the
explicit capture error distinguishes them from successfully captured empty
values. Contradictory available values and capture errors, duplicate capture
fields and empty accessor exception classes are rejected. Accessor failures
are diagnostic records rather than causal edges. Suppressed exceptions retain
their separate ordered relation, including their own causes and suppressed
exceptions. Both relation vectors and captureErrors are required wire fields.

The runner uses the assertion failure's cached rendering result. Reporting and
serialization do not render captured values again. Embedded exception messages
are captured lazily once and remain stable across repeated conversion. Rendered values obey the
assertion context's value limit; the diagnostic explicitly counts observations
omitted by its observation limit. Complete compiled text is retained independently
of the bounded display message. Channel frame limits still apply to the complete
serialized payload.

Durations in nanoseconds and event sequence numbers use decimal strings on the
wire, preserving the complete supported 64-bit integer range. JSON numbers
cannot do this through JavaScript's native parser, as documented by
[Circe](https://circe.io/circe/parsing.html) and tracked in
[circe/circe#393](https://github.com/circe/circe/issues/393). Schema version and
source line/column indices are 32-bit JSON integers.

Both encoding and decoding validate the schema. Invalid identities, empty
explicit selections, invalid locations, negative durations/sequences and
inconsistent run correlations fail explicitly. Encoding throws an
`IllegalArgumentException`; decoding returns `ProtocolDecodeError`.
The frame limit is 1,048,576 UTF-16 characters; a larger frame is rejected.
JSON container nesting is bounded at 128 before parsing. Failure graph nesting
through either causes or suppressed exceptions is bounded at 32, counting the
root failure as depth 1. Excess protocol depth is rejected without truncation.
The runner's Throwable converter explicitly reports cycles and depth truncation
as Transport failures; it does not produce an invalid over-depth record.
Quotes, escaped characters and braces inside JSON strings
do not contribute to container nesting.
Succeeded and skipped results cannot carry failures. Aggregate success requires
no run-level failure, no result-level failure and no cancellation.
Unknown schemas and message kinds are errors. Empty diagnostic source identities,
negative source indices, reversed ranges and negative omitted-observation counts
are rejected by both producer and consumer validation.

`ProtocolCodec.validate` checks payload schema without applying the serialized
frame-size limit. It can validate an in-process catalogue or result without
creating a channel frame. `encode` and `decode` enforce the channel limit;
schema validation alone does not certify that a payload fits a frame.

The protocol fixtures use independent checks and a fixed golden frame on each
compiler/platform lane. Separate builds consume locally published artifacts;
the JVM fixture also exchanges strings between two isolated classloaders.
The status ledger records the commands actually run and distinguishes those
checks from the pending full runner and transport gates.
