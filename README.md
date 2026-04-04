# openai_experiments

Small Clojure experiments for learning and testing OpenAI APIs.

## Requirements

- `OPENAI_API_KEY` must be set before running the demo or using the API functions.
- Never print or commit API keys.

## Run the Demo

Start a REPL:

```bash
clojure
```

Or start Rebel Readline:

```bash
clojure -M:rebel
```

Run the demo entry point:

```bash
clojure -M -m openai.core
```

## Run Tests

Run the test suite directly with Clojure:

```bash
clojure -M:test -m openai.test-runner
```

Run the same test suite through babashka:

```bash
bb test
```

There is no `build.clj` workflow in this repo now. The babashka task calls the JVM test runner directly.

## Verify the Namespace Loads

```bash
clojure -e "(require 'openai.core)"
```
