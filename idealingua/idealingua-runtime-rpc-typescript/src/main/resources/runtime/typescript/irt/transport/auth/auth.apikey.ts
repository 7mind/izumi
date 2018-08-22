import { AuthMethod } from './auth.method';

export class AuthApiKey extends AuthMethod {
    public apiKey: string;
    constructor(apiKey: string) {
        super();
        this.apiKey = apiKey;
    }

    public fromValue(value: string): Error {
        if (value.toLowerCase().indexOf('api-key ') === 0) {
            this.apiKey = value.substr(8);
            return undefined;
        }

        if (value.toLowerCase().indexOf('apikey ') === 0) {
            this.apiKey = value.substr(7);
            return undefined;
        }

        return new Error('api key authorization must start with ApiKey, got ' + value)
    }

    public toValue(): string {
        return 'Api-Key ' + this.apiKey;
    }
}
