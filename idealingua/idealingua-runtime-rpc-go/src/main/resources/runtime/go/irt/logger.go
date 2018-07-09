package irt

import (
	"fmt"
	"time"
)

type LogLevel int

const (
	LogTrace LogLevel = iota
	LogDebug
	LogInfo
	LogWarning
	LogError
)

const (
	timestampLayout = "2006-01-02T15:04:05.000000-07:00"
)

func levelToString(level LogLevel) string {
	switch level {
	case LogTrace:
		return "TRACE"
	case LogDebug:
		return "DEBUG"
	case LogInfo:
		return "INFO"
	case LogWarning:
		return "WARNING"
	case LogError:
		return "ERROR"
	default:
		return "UNKNOWN_LEVEL"
	}
}

type Logger interface {
	Logf(level LogLevel, format string, params ...interface{})
}

type consoleLogger struct {
	level LogLevel
}

func NewConsoleLogger(level LogLevel) Logger {
	res := &consoleLogger{}
	res.SetLevel(level)
	return res
}

func (l *consoleLogger) SetLevel(level LogLevel) {
	l.level = level
}

func (l *consoleLogger) GetLevel() LogLevel {
	return l.level
}

func (l *consoleLogger) Logf(level LogLevel, format string, params ...interface{}) {
	if l.level > level {
		return
	}

	now := time.Now()
	if level == LogError {
		fmt.Println("******************** ERROR ********************")
		fmt.Println("")
		fmt.Printf("[%s] %s: %s\n", now.Format(timestampLayout), levelToString(level), fmt.Sprintf(format, params...))
		fmt.Println("\n***********************************************")
	} else {
		fmt.Printf("[%s] %s: %s\n", now.Format(timestampLayout), levelToString(level), fmt.Sprintf(format, params...))
	}
}

type dummyLogger struct {
}

func NewDummyLogger() Logger {
	res := &dummyLogger{}
	return res
}

func (l *dummyLogger) Logf(level LogLevel, format string, params ...interface{}) {
}
