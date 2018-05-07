# Rendering policy

Rendering policy defines how incoming log event will be transformed into a string. Rendering layout defines in a [logstage](config.md) config section. 
Rendering policy has string interpolator-like format. Each component (named as _Log Unit_) defines like ${log_unit_label} in string.

## Log units specification

Unit     | Aliases                | Explanation                                  | Syntax  |
------------| ---------------------- | -------------------------------------------- | -------  
`timestamp` | `ts`                   | Timestamp                                    | `${timestamp}` |
`thread`    | `t`                    | Thread data (contains thread `name` and `id`) | `${thread}` |
`level`     | `lvl`                  | Logging level |  `${lvl}` |
`location`  | `loc`                  | Log message location (hyperlink to filename it's line number) | `${location}` | 
`message`   | `msg`                  | Application-supplied message associated with the logging event | `${message}` |
`custom-ctx`| `context`, `ctx`       | User's context (more info [here](custom_ctx.md))| `${custom-ctx}` |
`exception` | `ex`                   | Outputs the stack trace of the exception associated with the logging event, if any. By default the full stack trace will be output. | `${ex}` |

### Parameters

- Each log unit has parameters. (example, padding, margins, etc). There are common parameters for all log units and specific for each one. 
- NOTE! Currently there are only common parameters (padding). Parameters enumerates in `[` `]` braces. For example, `${message[15]}`

Log unit    | Features | Explanation | Example | 
------------| ---------------------- | -------------------------------------------- | ------ |
`common`    | `padding`              | padding for log unit in result string. It maybe default padding or ellipsed (`[:..14]`) | `${timestamp}[14]` |

### TODO:
- define params for each log unit 
- provide intuitive syntax for their definition
- tests!

