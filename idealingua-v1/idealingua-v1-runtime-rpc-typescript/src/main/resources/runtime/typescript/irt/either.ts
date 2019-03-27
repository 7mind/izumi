
export type Either<L, A> = Left<L, A> | Right<L, A>;

export class Left<L, A> {
    constructor(readonly value: L) {
    }

    public map<B>(f: (a: A) => B): Either<L, B> {
        return this as any;
    }

    public bimap<V, B>(g: (a: A) => B, f: (l: L) => V): Either<V, B> {
        return new Left(f(this.value));
    }

    public fold<B>(whenRight: (a: A) => B, whenLeft: (l: L) => B): B {
        return whenLeft(this.value);
    }

    public bifold<V, B>(whenRight: (a: A) => B, whenLeft: (l: L) => V): V | B {
        return whenLeft(this.value);
    }

    public getOrElse(a: A): A {
        return a;
    }

    public isLeft(): this is Left<L, A> {
        return true;
    }

    public isRight(): this is Right<L, A> {
        return false;
    }

    public swap(): Either<A, L> {
        return new Right(this.value);
    }

    public filterOrElse(p: (a: A) => boolean, zero: L): Either<L, A> {
        return this;
    }

    public match(whenRight: (value: A) => void, whenLeft:  (value: L) => void): void {
        whenLeft(this.value);
    }
}

export class Right<L, A> {
    constructor(readonly value: A) {
    }

    public map<B>(f: (a: A) => B): Either<L, B> {
        return new Right(
            f(this.value)
        );
    }

    public bimap<V, B>(g: (a: A) => B, f: (l: L) => V): Either<V, B> {
        return new Right<V, B>(
            g(this.value)
        );
    }

    public fold<B>( whenRight: (a: A) => B, whenLeft: (l: L) => B): B {
        return whenRight(this.value);
    }

    public bifold<V, B>(whenRight: (a: A) => B, whenLeft: (l: L) => V): V | B {
        return whenRight(this.value);
    }

    public getOrElse(a: A): A {
        return this.value;
    }

    public isLeft(): this is Left<L, A> {
        return false;
    }

    public isRight(): this is Right<L, A> {
        return true;
    }

    public swap(): Either<A, L> {
        return new Left(this.value);
    }

    public filterOrElse(p: (a: A) => boolean, zero: L): Either<L, A> {
        return p(this.value) ? this : left(zero);
    }

    public match(whenRight: (value: A) => void, whenLeft:  (value: L) => void): void {
        whenRight(this.value);
    }
}

const of = <L, A>(a: A): Either<L, A> => {
    return new Right<L, A>(a);
};

export const left = <L, A>(l: L): Either<L, A> => {
    return new Left(l);
};

export const right = of;
