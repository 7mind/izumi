import { AuthMethod } from './auth';

export class AuthCustom extends AuthMethod {
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
