package training

import "fmt"

type Log struct {
	dogName string
	points  int
}

func NewLog(dogName string, startingPoints int) *Log {
	return &Log{
		dogName: dogName,
		points:  startingPoints,
	}
}

func (l *Log) DogName() string {
	return l.dogName
}

func (l *Log) Points() int {
	return l.points
}

func (l *Log) Reward(points int) error {
	if points < 0 {
		return fmt.Errorf("reward points cannot be negative")
	}

	l.points += points
	return nil
}
