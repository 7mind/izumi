import { Logger, LogLevel } from './logger';

export class DummyLogger implements Logger {
    public logf(level: LogLevel, ...args: any[]) {
        // Do nothing here...
    }
}
