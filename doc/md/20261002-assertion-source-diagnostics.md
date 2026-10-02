# Assertion source and diagnostic contract

`fundamentals-assertions` supplies `izumi.fundamentals.assertions.Assert.assert`.
The one-argument form uses a fresh standard context. The two-argument form takes
an explicit `AssertionContext` containing the source root, source provider,
value renderer, and output limits. It returns `Unit` and throws `AssertionFailure`
when its Boolean argument is false. Exceptions from the condition propagate
unchanged. Definitions and consumers compile separately, including test sources.
An assertion evaluates its receiver, condition, and explicit context once, in
that order; an exception prevents evaluation of later arguments.

The Scala 2 implementation uses blackbox macros; Scala 3 uses the public quoted
API. Both emit the same portable runtime representation. Resolved built-in
Boolean conjunction, disjunction, and negation retain short-circuit evaluation.
Comparisons owned by Scala's primitive types, `Any`, `AnyRef`, or `Object` retain
their resolved method and capture each operand once. Other expressions are
evaluated unchanged and produce one opaque Boolean observation. In particular,
user-defined operators keep their by-name arguments, and array equality keeps
its reference semantics. Each invocation has its own recorder; skipped sites
carry `Evaluation.NotEvaluated`.
Inline helper calls remain opaque observations at the caller's source location,
so positions from an expanded helper are never attributed to the calling file.

Source offsets and columns count UTF-16 code units, start at zero, and use an
exclusive end offset. Lines also start at zero in the structured model; messages
display lines and columns starting at one. A `SourceSpan.Range` carries both
endpoints. A compiler position without a range becomes `SourceSpan.Point`;
absence of a position becomes `SourceSpan.Unavailable`. Text is copied from the
compiler's source buffer only when an exact range is available, otherwise
`CompiledText.Unavailable` records its absence. No compiler pretty-print is
substituted for the compiled excerpt. Scala 2.12 without `-Yrangepos`, and
Scala 2.13 with `-Yrangepos:false`, use point positions even if a typed tree
contains a synthesized range: such ranges can omit the left comparison operand
and therefore do not establish an exact expression span.

Physical paths replace backslashes with slashes and normalize `.` and `..`
lexically. With an explicit `SourceRoot.Directory`, paths underneath that root
become `SourceIdentity.Relative`. Other physical paths retain an explicit
absolute or relative identity. Virtual compiler sources carry
`SourceIdentity.Virtual`. Normalization and rendering do not read the runtime
filesystem, so moving or packaging a suite does not remove its compiled excerpt.
Parent traversal preserves POSIX roots, Windows drive roots, and UNC server/share
roots; unresolved parents in relative paths remain explicit.
A Windows drive-relative source root such as `C:` cannot relativize an absolute
`C:/` source. The distinction follows [Windows path syntax](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file).

A `SourceProvider` may resolve the identity to available content. Rendering
compares the recorded range and excerpt with that content before accepting it.
Different content produces `SourceValidation.Mismatch` and a message identifying
the mismatch while displaying the compiled excerpt. Missing content, missing
ranges, and provider exceptions have separate representations. No pointer is
inferred from mismatching surrounding source.

Constructing a failure does not render values or read source files. Its first
`getMessage` access renders and caches a `RenderedAssertion`, which preserves
the original diagnostic and exposes value-rendering exceptions separately.
The standard output limits are 256 characters per value, 1024 per excerpt,
32 observations, and 4096 characters overall. User renderers control their own
computation; these bounds apply to output and the number of renderer calls.
Tab expansion and pointer construction respect the remaining total output budget
before allocation, including when a caller supplies a very large tab width.
Tabs expand to stops of width four, and surrogate pairs occupy one pointer
column. This is a diagnostic display convention rather than a claim about the
terminal width of every Unicode glyph.
Excerpt, value, and total-output truncation do not split valid surrogate pairs.

The assertion fixtures use an independent exception-and-counter oracle rather
than the assertion being tested. They are specified-origin behavioral checks of
the public macro/runtime boundary (Blackbox/Group). The module's `Test / test`
runs their entry point directly, without a ScalaTest or Scalactic dependency;
the base runner will take over this entry point in the later runner steps.
