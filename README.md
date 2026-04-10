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
clojure -e "(require 'openai.audio)"
```

## API Examples

Require the namespaces you want to use from the REPL:

```clojure
(require '[openai.completions :as completions])
(require '[openai.responses :as responses])
(require '[openai.audio :as audio])
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

### Audio API

`openai.audio` currently covers transcription, translation, and text-to-speech.

#### Transcription

`openai.audio/transcribe-audio` sends an audio transcription request and returns the `:text` from the decoded response body.

If `:model` is omitted, it defaults to `"gpt-4o-mini-transcribe"`.

```clojure
(audio/transcribe-audio "ww2.mp3")
;; => "..."
```

The source can also be a remote audio URL. Remote files are downloaded to a temporary file before upload.

```clojure
(audio/transcribe-audio
 "https://example.com/audio/sample.wav"
 {:prompt "This is a history lecture."
  :language "en"})
;; => "..."
```

Supported options are passed through as multipart fields:

```clojure
{:model "gpt-4o-transcribe"
 :prompt "Optional prompt"
 :language "en"
 :temperature 0}
```

#### Translation

`openai.audio/translate-audio` sends an audio translation request and returns the translated text.

If `:model` is omitted, it defaults to `"whisper-1"`.

```clojure
(audio/translate-audio "ww2.mp3")
;; => "..."
```

It accepts the same option keys as `transcribe-audio`.

#### Text To Speech

`openai.audio/tts` sends a text-to-speech request and writes the returned audio bytes to a file.

If options are omitted, it defaults to:

```clojure
{:model "gpt-4o-mini-tts"
 :voice "alloy"
 :format "mp3"}
```

When `:output-path` is omitted, the function writes to a temporary file and returns that path.

```clojure
(audio/tts "Hello world")
;; => "/tmp/openai-speech-....mp3"
```

You can also provide a local text file or a remote text URL as the input source:

```clojure
(audio/tts "notes.txt"
           {:voice "nova"
            :format "wav"
            :output-path "/tmp/notes.wav"})
;; => "/tmp/notes.wav"

(audio/tts "https://example.com/script.txt"
           {:instructions "Read this like a radio host."})
;; => "/tmp/openai-speech-....mp3"
```

`openai.audio/speak-audio` is currently an alias for `openai.audio/tts`.
