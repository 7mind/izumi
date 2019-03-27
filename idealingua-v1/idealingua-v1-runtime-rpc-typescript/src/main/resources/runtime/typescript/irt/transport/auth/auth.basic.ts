import { AuthMethod } from './auth.method';

export class AuthBasic extends AuthMethod {
    public user: string;
    public pass: string;

    constructor(user: string = undefined, pass: string = undefined) {
        super();
        this.user = user;
        this.pass = pass ? btoa(pass) : undefined;
    }

    public getPass(): string {
        return atob(this.pass);
    }

    public fromValue(value: string): Error {
        const basicPieces = value.split(':');
        if (basicPieces.length !== 2) {
            return new Error('basic authorization update expects "user:pass" format, got ' + value);
        }

        this.user = basicPieces[0];
        this.pass = basicPieces[1];
        return undefined;
    }

    public toValue(): string {
        return 'Basic ' + this.user + ':' + this.pass;
    }
}
