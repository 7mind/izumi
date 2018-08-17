
import { Authorization } from '../auth';

export class SystemContext {
    public auth: Authorization;

    constructor() {
        this.auth = new Authorization();
    }
}