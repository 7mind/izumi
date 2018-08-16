
import { SystemContext } from './transport.context.system';

export class ConnectionContext<C> {
    public system: SystemContext;
    public user: C;

    constructor(user: C = undefined) {
        this.system = new SystemContext();
        this.user = user;
    }
}