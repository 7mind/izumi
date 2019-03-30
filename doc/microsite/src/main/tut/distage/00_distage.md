---
out: index.html
---

distage: Staged Dependency Injection
===================================

`distage` is a pragmatic module system for Scala that combines simplicity and reliability of pure FP with extreme late-binding
and flexibility of runtime dependency injection frameworks such as Guice.

`distage` is unopinionated, it's good for structuring applications written in either imperative Scala as in Akka/Play,
or in pure FP @ref[Tagless Final Style](basics.md#tagless-final-style)

```scala mdoc:invisible
// or with [ZIO Environment](#zio).
```

Further reading:

- Slides: [distage: Purely Functional Staged Dependency Injection](https://www.slideshare.net/7mind/distage-purely-functional-staged-dependency-injection-bonus-faking-kind-polymorphism-in-scala-2)
- @ref[Tutorial](basics.md#tutorial)
- @ref[Config Injection](config_injection.md)
- @ref[Other features](other-features.md)
- @ref[Debugging](debugging.md)
- @ref[Cookbook](cookbook.md)
- @ref[Syntax reference](reference.md)

## PPER

See @ref[PPER Overview](../pper/00_pper.md)

@@@ index

* [Basics](basics.md)
* [Config Injection](config_injection.md)
* [Other features](other-features.md)
* [Debugging](debugging.md)
* [Cookbook](cookbook.md)
* [Syntax reference](reference.md)

@@@
