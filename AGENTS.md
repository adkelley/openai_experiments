# AGENTS.md

## Project
This is a small Clojure experiment repo for learning and testing OpenAI APIs.

## Run
- Start a REPL with `clojure` or `clojure -M:rebel`
- Run the demo entry point with `clojure -M -m openai.main`

## Code Layout
- Main namespace: `openai.main`
- Current OpenAI chat completions function: `openai.completions/llm-request-completions`

## Environment
- Requires `OPENAI_API_KEY` to be set before starting the REPL or running the program
- Never print, log, or commit API keys
- If exception data includes headers, authorization must be redacted

## Dependencies
- HTTP client: `hato`
- JSON: `cheshire`

## Editing Guidance
- Keep this repo simple and educational
- Prefer small functions with clear names
- When adding support for another OpenAI API, create a separate function with an API-specific name
- Do not replace working experimental code with large abstractions unless requested

## Commits
- Follow the commit message approach described in `docs/commit.md`
- Prefer commit messages in the form `<symbol> <scope> - <summary>`

## Verification
- After changes, verify the namespace loads with:
  `clojure -e "(require 'openai.main)"`
- If relevant, test the function from the REPL with a simple prompt
