
export enum LogLevel {
    Trace,
    Debug,
    Info,
    Warning,
    Error
}

export interface Logger {
    logf(level: LogLevel, ...args: any[]): void
}
