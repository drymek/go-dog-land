package features_test

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/cucumber/godog"
	"github.com/drymek/GoDogland/internal/training"
)

type trainingFeature struct {
	log *training.Log
}

func TestFeatures(t *testing.T) {
	paths := []string{"."}
	if featurePath := os.Getenv("GODOGLAND_BDD_FEATURE"); featurePath != "" {
		paths = []string{featurePathForTest(featurePath)}
	}

	suite := godog.TestSuite{
		ScenarioInitializer: InitializeScenario,
		Options: &godog.Options{
			Format:   "pretty",
			Paths:    paths,
			TestingT: t,
		},
	}

	if suite.Run() != 0 {
		t.Fatal("non-zero status returned, failed to run feature tests")
	}
}

func InitializeScenario(ctx *godog.ScenarioContext) {
	feature := &trainingFeature{}

	ctx.Before(func(ctx context.Context, sc *godog.Scenario) (context.Context, error) {
		feature.log = nil
		return ctx, nil
	})

	ctx.Step(`^a dog named "([^"]*)" has (\d+) training points$`, feature.aDogNamedHasTrainingPoints)
	ctx.Step(`^the dog receives (\d+) training points$`, feature.theDogReceivesTrainingPoints)
	ctx.Step(`^the dog should have (\d+) training points$`, feature.theDogShouldHaveTrainingPoints)
}

func featurePathForTest(path string) string {
	if _, err := os.Stat(path); err == nil {
		return path
	}

	trimmed := filepath.Base(path)
	if _, err := os.Stat(trimmed); err == nil {
		return trimmed
	}

	return path
}

func (f *trainingFeature) aDogNamedHasTrainingPoints(name string, points int) error {
	f.log = training.NewLog(name, points)
	return nil
}

func (f *trainingFeature) theDogReceivesTrainingPoints(points int) error {
	if f.log == nil {
		return fmt.Errorf("training log was not initialized")
	}

	return f.log.Reward(points)
}

func (f *trainingFeature) theDogShouldHaveTrainingPoints(expected int) error {
	if f.log == nil {
		return fmt.Errorf("training log was not initialized")
	}

	actual := f.log.Points()
	if actual != expected {
		return fmt.Errorf("expected %d training points, got %d", expected, actual)
	}

	return nil
}
