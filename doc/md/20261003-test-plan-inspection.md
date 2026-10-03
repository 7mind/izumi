# Prepared-plan inspection

`ResolvedSelection` contains the original request and selected descriptors after
suite configuration and explicit overrides have been resolved. Axis overrides
apply before effective-axis filters. An explicit memoization override takes
precedence over suite configuration. `PlannedSelection` adds a `PlanInspection`
to that selection without serializing executable providers or live values.

The distage provider projects the actual `PreparedRun`: runtime plans, recursive
memoization trees, individual test plans, initial environment failures and
individual planning failures. Operations identify imports, providers, instances,
references, sets, subcontexts, effects, resources and proxies. Dependency edges
come from those plans' predecessor links. Their sorted presentation is not an
execution-order guarantee. The engine's grouping and execution policy determines
the scopes; inspection does not build a second grouping policy.

Planning retains one declarative runtime module within each equal execution-
parameter group. Reconstructing its provider closures for each environment made
otherwise equal runtime operations differ on Scala.js and prevented merging.
The module belongs to that planning invocation; provisioned runtime values and
resources still belong to their execution scopes. Bootstrap bindings remain
part of environment equivalence. The nested sharing fixture supplies one shared
no-op shutdown binding explicitly, so its two configurations have that defined
equivalence on every platform.

Each dependency has a numeric ID within the description and a display label.
Identity follows actual DIKey equality. Distinct keys may have identical labels,
including two distinct set values with the same hash. Set implementation labels
do not render their bound instance or implementation hash. Basic named keys use
their existing IdContract representation. Types and labels describe bindings;
they do not serialize providers, locators, resources or arbitrary effect values.

Scope paths identify roots and descendants within one prepared plan. Runtime
scopes are roots; memoization scopes have parents; test scopes are individual
leaves. The current engine also retains its runtime plan as the root level plan
of a TestTree, and the projection retains both entries. A memoization-kind scope
therefore does not by itself imply that every displayed operation allocates an
application resource. Resource operations and scope membership show the actual
sharing boundaries. IDs and paths may differ on a later plan; logical test IDs
remain the persistent selection identity.

All referenced keys must appear in the key table. A dependency operation must
exist in the same scope or an ancestor; declared imports remain explicit
operations. Scope membership partitions its parent's successful tests. Test
leaves and per-test Planning failures cover the selection exactly once.
`RunSession` validates each provider and then remaps local keys and root paths
for aggregation. It preserves independent providers' sharing boundaries.

Planning may load configuration and execute bootstrap or user planning
extensions. Inspection itself does not provision the application resource
providers or execute test bodies. The runner's bootstrap lifecycle must remain
open while executing its prepared plan; an inspection description cannot replace
that in-process ownership. Executing `PlannedRun` uses the already prepared
plans and waits for their resource finalizers before emitting Finished.

Schema 4 carries resolved and planned descriptions as correlated responses.
Encoding and decoding validate their selection, keys, scopes and failures.
The codec's frame-size limit applies to encoded descriptions; in-process payload
validation alone does not establish that a plan fits a transport frame. The
application dispatcher, channel and client integrations are separate work.
