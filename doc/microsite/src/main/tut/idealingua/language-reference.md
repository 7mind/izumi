Idealingua Language Reference
==================

# Keywords and aliases

Keyword     | Aliases                | Explanation                                 |
------------| ---------------------- | ------------------------------------------- |
`domain`    | `package`, `namespace` | Namespace containing collection of entities |
`import`    |                        | References a domain by id                   |
`include`   |                        | Includes `*.model` file by name             |
`alias`     | `type`, `using`        | Type alias                                  |
`enum`      |                        | Enumeration                                 |
`mixin`     | `interface`            | Mixin, named collection of fields           |
`data`      | `dto`, `struct`        | Data                                        |
`adt`       | `choice`               | Algebraic Data Type                         |
`id`        |                        | Identifier, named collection of scalars     |
`service`   |                        | Service interface                           |
`def`       | `fn`, `func`, `fun`    | Method                                      |

## Inheritance operators

Keyword     | Aliases                | Explanation                                           | Example                       |
------------| ---------------------- | ----------------------------------------------------- | ------------------------------|
`+`         | `+++`, `...`           | Inherit structure (copy fields)                       | `+ Mixin`                     |
`&`         | `&&&`                  | Inherit interface                                     | `& Mixin`                     |
`-`         | `---`                  | Drop structure or field (doesn't work for interfaces) | `- Mixin`, `- field: str`     |

# Built-in types

## Scalars

Type name   | Aliases                | Explanation                                 | Scala type                   |
------------| ---------------------- | ------------------------------------------- | -----------------------------|
`str`       | `string`               | String                                      | `String`                     |
`bool`      | `boolean`, `bit`       | Boolean                                     | `Boolean`                    |
`i08`       | `byte`, `int8`         | 8-bit integer                               | `Byte`                       |
`i16`       | `short`, `int16`       | 16-bit integer                              | `Short`                      |
`i32`       | `int`, `int32`         | 32-bit integer                              | `Int`                        |
`i64`       | `long`, `int64`        | 64-bit integer                              | `Long`                       |
`f32`       | `float`, `flt`         | Single precision floating point             | `Float`                      |
`f64`       | `double`, `dbl`        | Double precision floating point             | `Double`                     |
`uid`       | `uuid`                 | UUID                                        | `java.util.UUID`             |
`tsz`       | `dtl`, `datetimel`     | Timestamp with timezone                     | `java.time.ZonedDateTime`    |
`tsl`       | `dtz`, `datetimez`     | Local timestamp                             | `java.time.LocalDateTime`    |
`time`      | `time`                 | Time                                        | `java.time.LocalTime`        |
`date`      | `date`                 | Date                                        | `java.time.LocalDate`        |

*Notes:*

1. When the target language lacks a corresponding numeric type, we use the smallest type available that includes the required type. e.g. in TypeScript `i08` is `number`
2. When the target language lacks types for `time`

## Collections

Type name    | Explanation                                          | Scala mapping  |
------------ | ---------------------------------------------------- | -------------- |
`list[T]`    | List                                                 | `List`         |
`map[K, V]`  | Map (only scalar and `id` keys are supported)        | `Map`          |
`opt[T]`     | Optional value                                       | `Option`       |
`set[T]`     | Set (unordered)                                      | `Set`          |
