
import * as http from 'http';
import { Dispatcher, ServiceDispatcher } from '../../dispatcher';
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

    constructor(endpoint: string, port: number, services: ServiceDispatcher<C, string>[], logger: Logger, open: boolean = true,
                dispatcher: Dispatcher<C, string> = undefined, handlers: TransportHandlers<C> = undefined) {
        this._port = port;
        this._endpoint = endpoint;
        if (!dispatcher) {
            this._dispatcher = new Dispatcher<C, string>();
        } else {
            this._dispatcher = dispatcher;
        }

        services.forEach(s => {
            this._dispatcher.register(s);
        });

        this._logger = logger;
        this._handlers = handlers;
        this.requestHandler = this.requestHandler.bind(this);
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

        let headers = {};
        // IE8 does not allow domains to be specified, just the *
        // headers["Access-Control-Allow-Origin"] = req.headers.origin;
        headers['Access-Control-Allow-Origin'] = '*';

        if (method === 'OPTIONS') {
            headers['Access-Control-Allow-Methods'] = 'POST, GET, PUT, DELETE, OPTIONS';
            headers['Access-Control-Max-Age'] = '86400'; // 24 hours
            headers['Access-Control-Allow-Headers'] = 'Origin, X-Requested-With, X-HTTP-Method-Override, Content-Type, Accept';
            response.writeHead(200, headers);
            response.end();
            return;
        }

        headers['Content-Type'] = 'application/json';

        const endpointPos = url.indexOf(this._endpoint);
        if (endpointPos < 0) {
            const msg = 'Invalid endpoint hit: ' + url + '. Expected to use ' + this._endpoint;
            this._logger.logf(LogLevel.Error, msg);
            response.statusCode = 500;
            response.write(msg);
            response.end();
            return;
        }

        const urlEnd = url.substr(endpointPos + this._endpoint.length);
        const pieces = urlEnd.split('/');
        if (pieces.length != 3) {
            const msg = 'Invalid endpoint format. Expected to be /serviceName/serviceMethod, got ' + urlEnd;
            this._logger.logf(LogLevel.Error, msg);
            response.statusCode = 500;
            response.write(msg);
            response.end();
            return;
        }

        const rpcService = pieces[1];
        const rpcMethod = pieces[2];

        this._logger.logf(LogLevel.Trace, 'Incoming request:\n', url, method);
        let body: any[] = [];
        request
            .on('error', (err) => {
                this._logger.logf(LogLevel.Error, 'Error while serving request: ', err);
            })
            .on('data', (chunk) => {
                body.push(chunk);
            })
            .on('end', () => {
                const data = method === 'POST' ? Buffer.concat(body).toString() : null;
                this._logger.logf(LogLevel.Trace, data);
                response.on('error', (err) => {
                    this._logger.logf(LogLevel.Error, 'Error while serving response: ', err);
                });

                try {
                    this._dispatcher.dispatch(null, rpcService, rpcMethod, data)
                        .then((res) => {
                            this._logger.logf(LogLevel.Trace, 'Outgoing response:\n', res);
                            response.writeHead(200, headers);
                            response.write(res);
                            response.end();
                        })
                        .catch((err) => {
                            this._logger.logf(LogLevel.Trace, 'Outgoing response:\n', 500, err);
                            response.statusCode = 500;
                            response.write(err);
                            response.end();
                        });
                } catch (err) {
                    this._logger.logf(LogLevel.Warning, 'Dispatching failed:\n', err);
                    response.statusCode = 500;
                    response.write(err);
                    response.end();
                }
            });
    }
}

export class HttpServer extends HttpServerGeneric<object> {
}
