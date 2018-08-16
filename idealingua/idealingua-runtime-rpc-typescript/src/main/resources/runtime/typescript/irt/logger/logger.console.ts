
import { Formatter } from '../formatter';
import { Logger, LogLevel } from './logger';

export class ConsoleLogger implements Logger {
    private level: LogLevel;

    public constructor(level: LogLevel = LogLevel.Trace) {
        this.level = level;
    }

    private levelToNumber(level: LogLevel): number {
        switch (level) {
            case LogLevel.Trace: return 0;
            case LogLevel.Debug: return 1;
            case LogLevel.Info: return 2;
            case LogLevel.Warning: return 3;
            case LogLevel.Error: return 4;
            default: return 0;
        }
    }

    /* tslint:disable:no-console */
    public logf(level: LogLevel, ...args: any[]): void {
        if (this.levelToNumber(level) < this.levelToNumber(this.level)) {
            return;
        }

        let prefix = Formatter.writeZoneDateTime(new Date()) + ' [' + LogLevel[level] + ']: ';

        switch (level) {
            case LogLevel.Trace:
                console.log('%c' + prefix, 'color: grey; font-style: italic;', ...args);
                break;

            case LogLevel.Debug:
                console.log('%c' + prefix, 'color: grey; font-weight: normal;', ...args);
                break;

            case LogLevel.Info:
                console.log('%c' + prefix, 'color: blue; font-weight: normal;', ...args);
                break;

            case LogLevel.Warning:
                console.log('%c' + prefix, 'color: orange; font-weight: bold;', ...args);
                break;

            case LogLevel.Error:
                console.log('%c' + prefix, 'color: red; font-weight: bold;', ...args);
                break;

            default:
                console.log('%cUnknown LogLevel: ' + level, 'color: red; font-weight: bold;', ...args);
                break
        }
    }
    /* tslint:enable:no-console */
}
