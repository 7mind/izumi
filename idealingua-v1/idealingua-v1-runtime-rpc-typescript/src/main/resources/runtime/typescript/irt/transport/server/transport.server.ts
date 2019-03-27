
import * as http from 'http';
import { ConnectionContext } from './transport.context';

export interface TransportHandlers<C> {
    onConnect(context: ConnectionContext<C>, request: http.IncomingMessage): boolean
    onAuth(context: ConnectionContext<C>): boolean
    onDisconnect(context: ConnectionContext<C>): void
}
