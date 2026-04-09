# openai_experiments

Small Clojure experiments for learning and testing OpenAI APIs.

## Requirements

- `OPENAI_API_KEY` must be set before running the demo or using the API functions.
- Never print or commit API keys.

## Start a REPL

Start a REPL:

```bash
clojure
```

Or start Rebel Readline:

```bash
clojure -M:rebel
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

## Format Code

Format the Clojure source files with `cljfmt`:

```bash
clojure -M:fmt fix src test script
```

Or use babashka:

```bash
bb fmt
```

## Commits

Commits in this repo should follow the approach described in `docs/commit.md`.
Prefer the format `<symbol> <scope> - <summary>`.

## Verify the Namespaces Load

```bash
clojure -e "(require 'openai.completions)"
clojure -e "(require 'openai.files)"
clojure -e "(require 'openai.responses)"
```

## API Examples

Require the namespaces you want to use from the REPL:

```clojure
(require '[openai.completions :as completions])
(require '[openai.responses :as responses])
```

### Chat Completions

`openai.completions/request-text` sends a chat completions request and returns the assistant text.

```clojure
(completions/request-text
 [{:role "user"
   :content "What is the capital of France?"}])
;; => "Paris"
```

`openai.completions/llm-request` is an alias for the same function.

### Responses API

`openai.responses/request-response` sends a Responses API request and returns selected content from the decoded response body.

If `:model` is omitted from the request map, it defaults to `"gpt-5-mini"`.

#### Default text extraction

Pass `nil` as the selector to return the first assistant text found in the response.

```clojure
(responses/request-response
 {:input "What is the capital of France?"}
 nil)
;; => "The capital of France is Paris."
```

#### Exact path selection

Pass a vector path when you want a specific nested value from the response body.

```clojure
(responses/request-response
 {:input "What is the capital of France?"}
 [:output 1 :content 0 :text])
;; => "The capital of France is Paris."
```

This is useful for inspection, but it can be brittle because output indexes may vary.

#### Type-based selection

Pass a selector map when you want to find content by `:type` instead of by index.

```clojure
(responses/request-response
 {:input "What is the capital of France?"}
 {:output-type "message"
  :content-type "output_text"})
;; => "The capital of France is Paris."
```

You can also select a different field from the matched content item:

```clojure
(responses/request-response
 {:input "What are the top three stories on Hacker News today?"
  :tools [{:type "web_search"}]}
 {:output-type "message"
  :content-type "output_text"
  :field :annotations})
;; => [...]
```

If the selector does not match anything, the function throws an `ExceptionInfo` with the selector and decoded response body in `ex-data`.
