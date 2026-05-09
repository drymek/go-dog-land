package main

import (
	"fmt"

	"github.com/drymek/GoDogland/internal/training"
)

func main() {
	log := training.NewLog("Frela", 3)
	log.Reward(2)

	fmt.Printf("%s has %d training points\n", log.DogName(), log.Points())
}
