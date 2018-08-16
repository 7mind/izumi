package irt

import (
	"bytes"
	"crypto/tls"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"
)

type httpClientTransport struct {
	// Implements ServiceClientTransport
	endpoint   string
	client     *http.Client
	transport  *http.Transport
	auth       *Authorization
	logger     Logger
	marshaller Marshaller
	headers    TransportHeaders
}

func NewHTTPClientTransportEx(endpoint string, timeout time.Duration, skipSSLVerify bool, marshaller Marshaller, logger Logger) ClientTransport {
	transport := &http.Transport{
		TLSClientConfig:       &tls.Config{InsecureSkipVerify: skipSSLVerify},
		ExpectContinueTimeout: timeout,
		ResponseHeaderTimeout: timeout,
	}
	client := &http.Client{Transport: transport}
	return &httpClientTransport{
		endpoint:   endpoint,
		transport:  transport,
		client:     client,
		logger:     logger,
		marshaller: marshaller,
		headers:    map[string]string{},
	}
}

func NewHTTPClientTransport(endpoint string, timeout time.Duration) ClientTransport {
	return NewHTTPClientTransportEx(endpoint, timeout, false, NewJSONMarshaller(false), NewDummyLogger())
}

func (c *httpClientTransport) SetAuthorization(auth *Authorization) error {
	c.auth = auth
	return nil
}

func (c *httpClientTransport) GetAuthorization() *Authorization {
	return c.auth
}

func (c *httpClientTransport) GetHeaders() TransportHeaders {
	return c.headers
}

func (c *httpClientTransport) SetHeaders(headers TransportHeaders) error {
	c.headers = headers
	return nil
}

func (c *httpClientTransport) Send(service string, method string, dataIn interface{}, dataOut interface{}) error {
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

	if c.headers != nil {
		for k, v := range c.headers {
			req.Header.Set(k, v)
		}
	}

	if c.auth != nil {
		authValue := c.auth.ToValue()
		c.logger.Logf(LogDebug, "Header: Authorization: %s", authValue)
		req.Header.Set("Authorization", authValue)
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

	if dataOut != nil {
		if err := c.marshaller.Unmarshal(respBody, dataOut); err != nil {
			msg := fmt.Sprintf("error while unmarshalling data %+v Body: %s. Endpoint: %s", err, string(respBody), url)
			c.logger.Logf(LogError, msg)
			return fmt.Errorf(msg)
		}
	}

	c.logger.Logf(LogDebug, "================================================")
	return nil
}
