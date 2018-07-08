package irt

import "encoding/json"

type Marshaller interface {
	Marshal(data interface{}) ([]byte, error)
	Unmarshal(data []byte, model interface{}) error
}

type JSONMarshaller struct {
	pretty bool
}

func NewJSONMarshaller(pretty bool) *JSONMarshaller {
	res := &JSONMarshaller{}
	res.pretty = pretty
	return res
}

func (m *JSONMarshaller) Marshal(data interface{}) ([]byte, error) {
	if m.pretty {
		return json.MarshalIndent(data, "", "    ")
	} else {
		return json.Marshal(data)
	}
}

func (m *JSONMarshaller) Unmarshal(data []byte, model interface{}) error {
	return json.Unmarshal(data, model)
}
