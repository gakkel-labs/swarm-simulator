# Problem sheet: find a wreck

> Version française : [`problem-find-a-wreck.fr.md`](problem-find-a-wreck.fr.md)

## 1. The problem

The operator prepares a mission to search for a sunken wreck. The operator does not know which fleet configuration to choose (number of detection drones, specialised drones, search pattern), or if the wreck is really inside the search area. Gakkel helps the operator choose the configuration with the best chance to succeed, without putting the most important drones at risk.

## 2. A successful mission

A mission is successful if it **ends before the limit time** (mandatory end: drone energy, weather) and one of these two cases is true:

1. **The wreck is found**: detected, then confirmed.
2. **Absence is shown**: the search area is covered above the **coverage threshold** chosen by the operator (95% by default), and every **contact** is classified by a specialised drone.

The operator is never sure that the wreck is inside the search area, so showing its absence is also a success. Going over the **estimated time** makes the mission late, but not a failure.

## 3. The north star metric

**Mission success probability.**

Before the mission, the backend computes it on a **common set of scenarios**: the wreck at position A, at position B, at position C… (spread across the search area), plus scenarios with no wreck. Every fleet configuration is simulated on exactly the same scenarios, so the comparison is fair and the result can be reproduced. The success probability is the share of successful scenarios: "the mission succeeds in 87 scenarios out of 100". It is a prediction.

With real missions, the metric becomes the **share of real missions that succeed**.

## 4. Guardrails

| Guardrail | Type | Limit |
|---|---|---|
| Drone carrier lost | hard | 0, never changeable |
| Specialised drones lost | hard | 0 by default, changeable for one mission with the manager's approval |
| Detection drones lost | soft | as few as possible |

A configuration that breaks a **hard** guardrail is removed. A **soft** guardrail is watched, but removes nothing.

## 5. Secondary indicators

- **Estimated time**: the expected duration; the shorter it is, the more margin it leaves before the limit time.
- **Resources used**: drone carrier hours, drones used, running hours, energy. They help to estimate the cost of a mission.
- **Fleet wear**: running hours, battery cycles, hits against the seabed. It tells when the fleet will be available again.

## 6. In case of a tie

There is no single rule: depending on the mission, the operator prefers safety, speed or saving the fleet. Among the configurations that respect the hard guardrails, the backend proposes three: **the safest** (best success probability), **the fastest** (shortest estimated time) and **the most economical** (fewest resources used and least wear). If two configurations are equal on their criterion, we keep the one that loses fewer detection drones. The operator chooses.
