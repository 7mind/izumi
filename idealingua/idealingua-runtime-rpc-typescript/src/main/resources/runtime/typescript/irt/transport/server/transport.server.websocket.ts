
import * as http from 'http';
import { server as wsServer, request as wsRequest, connection as wsConnection, IMessage } from 'websocket';
import { Dispatcher, ServiceDispatcher } from '../../dispatcher';
import { Logger, LogLevel } from '../../logger';
import { TransportHandlers } from "./transport.server";

export class WebSocketServerGeneric<C, D> {
    private _server: wsServer;
    private _dispatcher: Dispatcher<C, D>;
    private _logger: Logger;
    private _handlers: TransportHandlers<C>;
    private _connections: wsConnection[];

    constructor(server: http.Server, services: ServiceDispatcher<C, D>[], logger: Logger, open: boolean = true,
                dispatcher: Dispatcher<C, D> = undefined, handlers: TransportHandlers<C> = undefined) {
        this._logger = logger;
        if (!dispatcher) {
            this._dispatcher = new Dispatcher<C, D>();
        } else {
            this._dispatcher = dispatcher;
        }

        services.forEach(s => {
            this._dispatcher.register(s);
        });

        this._handlers = handlers;
        this._connections = [];
        this._server = new wsServer({
            // WebSocket server is tied to a HTTP server. WebSocket
            // request is just an enhanced HTTP request. For more info
            // http://tools.ietf.org/html/rfc6455#page-6
            httpServer: server
        });

        this._server.on('request', (request: wsRequest) => {
            this._logger.logf(LogLevel.Trace, 'Incoming connection from ' + request.origin);
            var connection = request.accept(null, request.origin);

            if (this._handlers) {
                if (!this._handlers.onConnect(null, null)) {
                    connection.close();
                    return;
                }
            }

            this._connections.push(connection);

            connection.on('message', (message: IMessage) => {
                if (message.type !== 'utf8' || !message.utf8Data) {
                    this._logger.logf(LogLevel.Warning, 'Non textual format is not supported. ', message);
                    return;
                }

                const data = message.utf8Data;
                throw new Error('Not implemented!');
            });

            connection.on('error', (err: Error) => {
                this._logger.logf(LogLevel.Error, 'Error received: ', err);
            });

            connection.on('close', (code: number, desc: string) => {
                if (this._handlers) {
                    this._handlers.onDisconnect(null);
                }

                this._connections = this._connections.filter(c => c !== connection);
            });
        });
    }
}
