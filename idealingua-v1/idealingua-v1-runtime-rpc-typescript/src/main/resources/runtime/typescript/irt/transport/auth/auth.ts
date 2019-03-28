import { AuthMethod } from './auth.method';
import { AuthCustom } from './auth.custom';
import { AuthToken } from './auth.token';
import { AuthApiKey } from './auth.apikey';
import { AuthBasic } from './auth.basic';

export class Authorization {
    public method: AuthMethod;

    constructor() {}

    public updateFromValue(auth: string): Error {
        const pieces = auth.split(' ');
        if (pieces.length !== 2) {
            if (auth.length > 0) {
                this.method = new AuthCustom(auth);
                return undefined;
            }

            return new Error('authorization update expects "type value" format, got ' + auth);
        }

        switch (pieces[0].toLowerCase()) {
            case 'bearer': this.method = new AuthToken(pieces[1]);
                break;
            case 'apikey':
            case 'api-key':
                this.method = new AuthApiKey(pieces[1]);
                break;
            case 'basic': {
                const basic = new AuthBasic();
                basic.fromValue(pieces[1]);
                this.method = basic;
            } break;
            default:
                this.method = new AuthCustom(auth);
                // return new Error('unsupported authorization mechanism ' + auth);
        }

        return undefined;
    }

    public toValue(): string {
        if (!this.method) {
            return '';
        }
        return this.method.toValue()
    }
}
