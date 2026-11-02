# RHI: Recursive Human Intelligence

AI improves itself recursively. Why shouldn't you?

RHI reads your chats with AI assistants and hands you feedback, sources, and tools so the same questions stop coming back.

## Start on Windows

Requires JDK 17+. The launcher finds an installed JDK even when an older Java comes first on PATH. In PowerShell:

```powershell
& "\\wsl.localhost\Ubuntu\home\andrea\rpi\desktop\start.bat"
```

Or launch `desktop/start.bat` from your checkout. It compiles all sources, runs local checks, and opens Loop. UNC/WSL folders are handled with `pushd`. On Linux/WSL with a graphical desktop, use `./desktop/start.sh`.

## Use it

Click **Get feedback** on Home. One click is a complete read: the agent keeps looking, up to four passes, until nothing strong is left. The button then becomes **Check for new chats**, which calls the model only if your chats changed. That one button does everything in order: asks for your AI connection if it is missing, checks for new chats, reads and groups what is new, and writes your feedback. Items appear as they arrive, and you can open them while it is still working.

**Patterns** and **Chats** are there to explore and to narrow things down; you never need them. The **>** at the bottom opens logs of each step: API calls with sizes, timing, and tokens, what each batch produced, each pattern and item as it lands, and anything dropped and why. Logs show model-written names, never keys or your messages.

## Patterns

1. **Patterns** starts as a quick local keyword map, with nothing sent. **Analyze with AI** reads your requests in batches of up to 250, keeping similar ones together, using the model from Settings, after stating how much will be sent. The map grows by area as batches return, then settles into named patterns grouped from the short descriptions alone. Results are saved, so later runs read only new requests. Chats are always counted locally. **Focus on** is optional and steers grouping and findings. Click bubbles to choose what to analyze, or click a color to filter.
2. **Get feedback**, on Home or Patterns, opens Home and sends the one-line description of every request, plus excerpts from your patterns, to your model. It returns a short list of named items. Click one for the detail, what to do, and a search for any source it names. Items must quote at least two real messages. Phrases you retype across chats are measured locally and shown to the agent. **Mark as done** moves an item to a hidden list below, where **Undo** brings it back, and it will not be suggested again. **Remove**, on done items and in the detail view, forgets an item so it can come back.

## Connect a model

Settings supports three providers through one model interface:

| Provider | Default endpoint | Environment key |
|---|---|---|
| OpenAI | `https://api.openai.com/v1/chat/completions` | `OPENAI_API_KEY` |
| Claude | `https://api.anthropic.com/v1/messages` | `ANTHROPIC_API_KEY` |
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions` | `OPENROUTER_API_KEY` |

Enter a model ID available to your account. Paste a session key or set the corresponding environment variable before launching. Keys are never written to settings. Click **Test connection** and enable consent to send selected excerpts. Settings save themselves. Testing sends a small test prompt, not chat history. Custom endpoints require HTTPS, except localhost development endpoints.

Analysis samples at most 80 user excerpts / 45,000 text characters per call. Obvious credentials are redacted and session names replaced with anonymous codes before sending. Evidence must reference actual sampled requests from three distinct sessions. The app rejects invalid output and unsupported operations. Archived topics and similar titles are suppressed. Feedback and generated content are stored separately and are never imported as evidence.

## Files and generated tools

History and state live in `~/.loop/`: `history.tsv`, `scan-index.tsv`, `settings.properties`, `solutions.json`, and generated HTML artifacts. Unchanged files are not reparsed. Transcripts are streamed, up to 512 MB each. Only what the user typed is kept: injected context, slash commands, tool results, meta turns, and subagent sessions are dropped. No directory symlinks are followed. Windows discovers WSL homes; WSL checks Windows profiles. Environment location overrides are supported; `LOOP_WINDOWS_HOME` adds an explicit profile.

Generated HTML opens in your browser. It uses a sandboxed iframe, restrictive content policy, and an application-owned renderer. The model supplies plain content, quiz data, and allowlisted text operations (trim, remove empty lines, deduplicate, literal replace), never executable JavaScript. The CSV cleaner accepts a dropped or chosen file, recognizes comma, semicolon, and tab delimiters and UTF-8 or ANSI text, shows a preview, and saves a copy. Previous solution versions and explicit feedback remain available in the desktop library.

Exclude a project by its real folder, for example `/home/you/client-work`. Exclusions affect future imports. Previously imported history is retained until deletion and may still be used in analysis. Delete history removes imported requests and solution data, leaving original chat transcripts untouched. Cloud sync, signed installers, arbitrary scripts, and an MCP server are not included.

## Find saved solutions from an agent harness

After compiling, any harness with access to Java can look up active solutions:

```sh
java -cp desktop/build Loop --search "docker"
```

The command returns JSON with title, description, version, and a standalone artifact path. Archived tools are excluded; it performs no model calls and does not launch the desktop window.

## Validation

```sh
./desktop/check.sh
```

The Windows launcher runs the same checks: transcript formats, harness-text filtering, project exclusions, deduplication, cancellation, persistence, provider formats, evidence validation, cooldowns, feedback, revision and restore, CSV delimiters and encodings, redaction, and artifact escaping.

`desktop/ui-test/run.sh Drive` runs RHI's real window end to end from WSL with the Windows JDK: real controls, a throwaway home, and a fake local provider. It opens the window off-screen and draws only RHI's own components to images. `RealImport` reports counts from real transcripts without printing their text.
