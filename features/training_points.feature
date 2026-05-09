Feature: Training points
  Dog handlers want a simple way to track progress after training.

  Scenario: Rewarding a dog after a successful training session
    Given a dog named "Kefir" has 3 training points
    When the dog receives 2 training points
    Then the dog should have 6 training points

  Scenario: Giving first training points to a new dog
    Given a dog named "Frela" has 0 training points
    When the dog receives 4 training points
    Then the dog should have 4 training points
