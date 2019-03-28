
export abstract class AuthMethod {
    public abstract fromValue(value: string): Error;
    public abstract toValue(): string;
}

