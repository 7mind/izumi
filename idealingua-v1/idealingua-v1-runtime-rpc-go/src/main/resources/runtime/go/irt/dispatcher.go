package irt

import (
	"fmt"
)

type ServiceDispatcher interface {
	Dispatch(context interface{}, method string, data []byte) ([]byte, error)
	GetSupportedService() string
	GetSupportedMethods() []string
}

type Dispatcher struct {
	services map[string]ServiceDispatcher
}

func (d *Dispatcher) Register(dispatcher ServiceDispatcher) error {
	if dispatcher == nil {
		return fmt.Errorf("trying to register a nil dispatcher")
	}

	if d.services == nil {
		d.services = map[string]ServiceDispatcher{}
	}

	serviceName := dispatcher.GetSupportedService()
	_, exists := d.services[serviceName]
	if exists {
		return fmt.Errorf("trying to register a dispatcher for service %s which is already registered", serviceName)
	}

	d.services[serviceName] = dispatcher
	return nil
}

func (d *Dispatcher) Unregister(serviceName string) bool {
	if d.services == nil {
		return false
	}
	_, ok := d.services[serviceName]
	if ok {
		delete(d.services, serviceName)
		return true
	}

	return false
}

func (d *Dispatcher) Dispatch(context interface{}, service string, method string, data []byte) (out []byte, derr error) {
	if d.services == nil {
		return nil, fmt.Errorf("no services registered to dispatch to")
	}

	dispatcher, ok := d.services[service]
	if !ok {
		return nil, fmt.Errorf("no %s service dispatcher registered", service)
	}

	defer func() {
		if err := recover(); err != nil {
			out = []byte{}
			derr = fmt.Errorf("error in Dispatch for %s/%s: %+v", service, method, err)
		}
	}()

	return dispatcher.Dispatch(context, method, data)
}
