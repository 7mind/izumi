package irt

import (
    "bytes"
    "crypto/tls"
    "fmt"
    "io/ioutil"
    "net/http"
    "time"
)

type ServiceClientTransport interface {
    Send(service string, method string, dataIn interface{}, dataOut interface{}) error
}

type HTTPClientTransport struct {
    endpoint      string
    client        *http.Client
    transport     *http.Transport
    authorization string
    logger        Logger
    marshaller    Marshaller
}

func NewHTTPClientTransportEx(endpoint string, timeout int, skipSSLVerify bool, marshaller Marshaller, logger Logger) *HTTPClientTransport {
    transport := &http.Transport{
        TLSClientConfig:       &tls.Config{InsecureSkipVerify: skipSSLVerify},
        ExpectContinueTimeout: time.Millisecond * time.Duration(timeout),
        ResponseHeaderTimeout: time.Millisecond * time.Duration(timeout),
    }
    client := &http.Client{Transport: transport}
    return &HTTPClientTransport{
        endpoint:   endpoint,
        transport:  transport,
        client:     client,
        logger:     logger,
        marshaller: marshaller,
    }
}

func NewHTTPClientTransport(endpoint string, timeout int, skipSSLVerify bool) *HTTPClientTransport {
    return NewHTTPClientTransportEx(endpoint, timeout, skipSSLVerify, NewJSONMarshaller(false), NewDummyLogger())
}

func (c *HTTPClientTransport) GetEndpoint() string {
    return c.endpoint
}

func (c *HTTPClientTransport) SetEndpoint(endpoint string) {
    c.endpoint = endpoint
}

func (c *HTTPClientTransport) GetAuthorization() string {
    return c.authorization
}

func (c *HTTPClientTransport) SetAuthorization(auth string) {
    c.authorization = auth
}

func (c *HTTPClientTransport) GetLogger() Logger {
    return c.logger
}

func (c *HTTPClientTransport) SetLogger(logger Logger) {
    c.logger = logger
}

func (c *HTTPClientTransport) GetMarshaller() Marshaller {
    return c.marshaller
}

func (c *HTTPClientTransport) SetMarshaller(marshaller Marshaller) {
    c.marshaller = marshaller
}

func (c *HTTPClientTransport) Send(service string, method string, dataIn interface{}, dataOut interface{}) error {
    url := c.endpoint + service + "/" + method
    var req *http.Request
    var err error

    c.logger.Logf(LogDebug, "================================================")
    c.logger.Logf(LogDebug, "Requesting Service: %s, Method: %s", service, method)
    c.logger.Logf(LogDebug, "Endpoint: %s", url)
    if dataIn == nil {
        c.logger.Logf(LogDebug, "Method: GET")
        req, err = http.NewRequest("GET", url, nil)
    } else {
        c.logger.Logf(LogDebug, "Method: POST")

        body, err := c.marshaller.Marshal(dataIn)
        if err != nil {
            c.logger.Logf(LogError, "Data in marshalling failed: %s", err.Error())
            return err
        }
        req, err = http.NewRequest("POST", url, bytes.NewBuffer(body))
        if err != nil {
            c.logger.Logf(LogError, "Create POST request failed: %s", err.Error())
            return err
        }

        c.logger.Logf(LogTrace, "Request body:\n`%s`", string(body))
        c.logger.Logf(LogDebug, "Header: Content-Type: application/json")
        req.Header.Set("Content-Type", "application/json")
    }

    if c.authorization != "" {
        c.logger.Logf(LogDebug, "Header: Authorization: %s", c.authorization)
        req.Header.Set("Authorization", c.authorization)
    }

    resp, err := c.client.Do(req)
    if err != nil {
        c.logger.Logf(LogError, "Request failed: %s", err.Error())
        return err
    }
    c.logger.Logf(LogDebug, "Response Status: %d", resp.StatusCode)
    c.logger.Logf(LogDebug, "Response length: %d", resp.ContentLength)

    defer resp.Body.Close()
    respBody, err := ioutil.ReadAll(resp.Body)
    if err != nil {
        c.logger.Logf(LogError, "Response reading failed: %s", err.Error())
        return err
    }

    c.logger.Logf(LogTrace, "Response body:\n`%s`", string(respBody))
    if resp.StatusCode != 200 {
        msg := fmt.Sprintf("Server returned code %d. Response body: %s. Endpoint: %s", resp.StatusCode, string(respBody), url)
        c.logger.Logf(LogError, msg)
        return fmt.Errorf(msg)
    }

    if err := c.marshaller.Unmarshal(respBody, dataOut); err != nil {
        msg := fmt.Sprintf("error while unmarshalling data %+v Body: %s. Endpoint: %s", err, string(respBody), url)
        c.logger.Logf(LogError, msg)
        return fmt.Errorf(msg)
    }

    c.logger.Logf(LogDebug, "================================================")
    return nil
}
