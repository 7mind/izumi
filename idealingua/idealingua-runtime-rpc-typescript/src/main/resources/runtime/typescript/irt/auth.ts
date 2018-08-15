
export abstract class AuthMethod {
    public abstract fromValue(value: string): Error;
    public abstract toValue(): string;
}

export class AuthCustom extends AuthMethod{
    public value: string;
    constructor(value: string) {
        super();
        this.value = value;
    }

    public fromValue(value: string): Error {
        this.value = value;
        return undefined;
    }

    public toValue(): string {
        return this.value;
    }
}

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

export class AuthBasic extends AuthMethod{
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
        return this.method.toValue()
    }
}
