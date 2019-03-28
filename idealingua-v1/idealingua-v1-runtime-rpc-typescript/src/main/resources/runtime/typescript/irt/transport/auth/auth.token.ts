import { AuthMethod } from './auth.method';

export class AuthToken extends AuthMethod{
    public token: string;
    constructor(token: string) {
        super();
        this.token = token;
    }

    public fromValue(value: string): Error {
        if (value.indexOf('Bearer ') !== 0) {
            return new Error('token authorization must start with Bearer, got ' + value)
        }
        this.token = value.substr(7);
        return undefined;
    }

    public toValue(): string {
        return 'Bearer ' + this.token;
    }
}
