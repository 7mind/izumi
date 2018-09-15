
export interface ServiceDispatcher<C, D> {
    dispatch(context: C, method: string, data: D | undefined): Promise<D>;
    getSupportedService(): string;
    getSupportedMethods(): string[];
}

export class Dispatcher<C, D> {
    private services: {[key: string]: ServiceDispatcher<C, D>};
    constructor() {
        this.services = {};
    }

    public register(dispatcher: ServiceDispatcher<C, D>): boolean {
        const svc = dispatcher.getSupportedService();
        if (svc in this.services) {
            return false;
        }

        this.services[svc] = dispatcher;
        return true;
    }

    public unregister(serviceName: string): boolean {
        if (!(serviceName in this.services)) {
            return false;
        }

        delete this.services[serviceName];
        return true;
    }

    public dispatch(ctx: C, service: string, method: string, data: D | undefined): Promise<D> {
        if (!(service in this.services)) {
            throw new Error(`Service '${service}' is not registered with the dispatcher.`);
        }

        return this.services[service].dispatch(ctx, method, data);
    }
}
