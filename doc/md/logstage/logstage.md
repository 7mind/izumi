# Logstage


## [Logging contexts](contexts.md)
## [Available sink backends](sinks.md)
## [Rendering policy](policylayout.md)
## [Configuration](config.md)
## [Examples](sample.md)

## Keywords and aliases

Keyword     | Aliases                | Explanation                                  |
------------| ---------------------- | -------------------------------------------- |
`timestamp` | `ts`                   | Timestamp                                    |
`thread`    | `t`                    | Thread data (contains thread `name` and `id`) |
`level`     | `lvl`                  | Logging level |
`location`  | `loc`                  | Log message location (hyperlink to filename it's line number) |
`message`   | `msg`                  | Application-supplied message associated with the logging event |
`custom-ctx`| `context`, `ctx`       | User's context (more info [here](custom_ctx.md))|
`exception` | `ex`                   | Outputs the stack trace of the exception associated with the logging event, if any. 
                                       By default the full stack trace will be output. |

### Parameters

Each log unit has parameters. For example, padding, margins, etc. Also each log unit has unique ones. 
For example, Timestamp unit has it's format (i.e. local, ISO, "dd/mm/yy"). 
Table below describes all paremters for each log unit.

Log unit    | Features | Explanation                                  | Example | 
------------| ---------------------- | -------------------------------------------- | ------ |
`timestamp` | `ts`                   | Timestamp                                    | |
`thread`    | `t`                    | Thread data (contains thread `name` and `id`) | |
`level`     | `lvl`                  | Logging level | |
`location`  | `loc`                  | Log message location (hyperlink to filename it's line number) | |
`message`   | `msg`                  | Actual log message | |
`custom-ctx`| `context`, `ctx`       | User's context | |

### TODO : 
- implement datetime formats for timestamp
