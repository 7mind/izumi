# Session-owned plugin loader factories

The new runner's `DistageSpec` resolves `makePluginLoaderFactory()` when a
selected suite's environment is resolved. Discovery does not call the hook or
materialize its factory. `PluginLoaderFactoryConfiguration` supplies the hook
contract for stackable configuration traits. The inherited zero-argument
`makePluginloader()` is final. Custom loader hooks migrate to this factory API;
the legacy adapter has been retired.

```scala
override protected def makePluginLoaderFactory(): PluginLoaderFactory =
  new PluginLoaderFactory {
    override def create(cache: PluginPackageCache): PluginLoader = {
      val delegate = PluginLoaderDefaultImpl.withPackageCache(cache)
      new PluginLoader {
        override def load(config: PluginConfig): LoadedPlugins =
          delegate.load(PluginConfig(config.packagesEnabled, config.packagesDisabled,
            config.cachePackages, config.debug, config.merges, config.overrides))
      }
    }
  }
```

The supplied cache belongs to the session's distage provider. Bind every real
cache-bearing delegate to it before loading; reconstructed requests and borrowed
worker threads then retain that dependency without request metadata or thread
state. A custom `PluginLoaderDefaultImpl` subclass may override its protected
`packageCache` with the supplied cache. Compatible factories in one provider
share package instances, preserving captured definitions and eager `PluginBase`
transformations. `cachePackages=false` still bypasses package caching.

The provider keys factories by reference, materializes each reference once and
retains its returned loader. Equal but distinct factory instances are distinct
collaborators. The default factory is stable across the provider's suites. A
custom hook returning a stable factory reference can likewise share its loader.
A factory may be shared across sessions if its creation is safe for concurrent
calls: each provider supplies a distinct cache. Each creation attempt retains
either its loader or the original nonfatal exception; another lookup in that
provider does not retry. A new session performs a new attempt. Same-thread lookup
of the factory being materialized fails explicitly. Creation must not perform
recursive factory resolution or wait on another thread that calls it; the guard
does not detect cross-thread or cross-factory cycles. Creation runs under a lock
specific to that factory, outside the registry lock.

Factories construct declarative loaders using borrowed collaborators. They have
no lifecycle/release protocol for newly allocated threads or external resources.
They must not return mutable state from a previous session. The factory contract
cannot prove that an opaque delegate uses its supplied cache or clone captured
values, prebuilt modules, closures or stateful Scala object plugins (`MODULE$`).
Immutable definitions and stateless delegates may be shared. Resource ownership
still requires lifecycle bindings, and completion follows resource release.

One shared package cache assumes a compatible scanner/classloader/construction
domain. Its key contains package, whitelist and exclusions, rather than arbitrary
classloader or custom policy identity. A distinct policy must construct its own
compatible owner-local cache/delegate inside `create`; the runner does not replace
that collaborator or promise memoization between incompatible domains. Ordinary
zero-argument loaders retain their legacy cache/construction policy. JS/Native
retain static loading and explicit runtime-scanning rejection.

Roles and merge hooks still precede factory resolution. Application and bootstrap
loading, merging, configuration snapshots, effect selection and the distage engine
keep their existing order. The session environment cache uses the actual stable
loader reference, along with the existing config, role, merge, effect and default
module dimensions.

The owner approved this factory migration on 2026-10-03. Acceptance item 2b.10
permits hook/construction edits for custom plugin-loader hooks using this API;
their recorded migration diffs must verify the preserved behavior required by
O.1. Ordinary eligible suites retain the imports-only guarantee. The complete
migration-diff inventory and final evaluation remain open in the status ledger.
