
import * as http from 'http';
import { Dispatcher } from '../../dispatcher';
import { Logger, LogLevel } from '../../logger';
import {TransportHandlers} from "./transport.server";

export class HttpServerGeneric<C> {
    private _port: number;
    private _endpoint: string;
    private _server: http.Server;
    private _open: boolean;
    private _logger: Logger;
    private _handlers: TransportHandlers<C>;
    private _dispatcher: Dispatcher<C, string>;

    public get server() {
        return this._server;
    }

    constructor(endpoint: string, port: number, logger: Logger, open: boolean = true, handlers: TransportHandlers<C> = undefined) {
        this._port = port;
        this._endpoint = endpoint;
        this._logger = logger;
        this._handlers = handlers;
        this._server = http.createServer(this.requestHandler);
    }

    public open() {
        if (this._open) {
            return;
        }

        this._server.listen(this._port, (err: string) => {
            if (err) {
                this._logger.logf(LogLevel.Error, 'Failed to start server ' + err)
            }

            this._logger.logf(LogLevel.Info, 'Server is listening on port ' + this._port)
        });
    }

    protected requestHandler(request: http.IncomingMessage, response: http.ServerResponse) {
        const { method, url, headers } = request;

        const endpointPos = url.indexOf(this._endpoint);
        if (endpointPos < 0) {
            const msg = 'Invalid endpoint hit: ' + url + '. Expected to use ' + this._endpoint;
            this._logger.logf(LogLevel.Error, msg);
            response.statusCode = 500;
            response.write(msg);
            return;
        }

        const urlEnd = url.substr(endpointPos + this._endpoint.length);
        const pieces = urlEnd.split('/');
        if (pieces.length != 2) {
            const msg = 'Invalid endpoint format. Expected to be /serviceName/serviceMethod, got ' + urlEnd;
            this._logger.logf(LogLevel.Error, msg);
            response.statusCode = 500;
            response.write(msg);
            return;
        }

        const rpcService = pieces[0];
        const rpcMethod = pieces[1];

        var data: string = null;
        if (method === 'POST') {
            let body: any[] = [];
            request.on('data', (chunk: any) => {
                body.push(chunk);
            }).on('end', () => {
                data = Buffer.concat(body).toString();
            });
        }

        this._logger.logf(LogLevel.Trace, 'Incoming request:\n', url, method, data);

        this._dispatcher.dispatch(null, rpcService, rpcMethod, data)
            .then((res) => {
                this._logger.logf(LogLevel.Trace, 'Outgoing response:\n', res);
                response.write(res);
            })
            .catch((err) => {
                this._logger.logf(LogLevel.Trace, 'Outgoing response:\n', 500, err);
                response.statusCode = 500;
                response.write(err);
            });
    }
}

export class HttpServer extends HttpServerGeneric<object> {
}
