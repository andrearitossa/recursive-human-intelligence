# Loop

A Java desktop companion that turns recurring work into personal tools.

## Start on Windows

Requires JDK 17+. The launcher finds an installed JDK even when an older Java comes first on PATH. In PowerShell:

```powershell
& "\\wsl.localhost\Ubuntu\home\andrea\rpi\desktop\start.bat"
```

Or launch `desktop/start.bat` from your checkout. It compiles all sources, runs local checks, and opens Loop. UNC/WSL folders are handled with `pushd`. On Linux/WSL with a graphical desktop, use `./desktop/start.sh`.

## Use the loop

1. On **Chats**, **Import chats** reads default Codex, Claude, and Pi folders. **Add folder** adds a chat folder or a home folder. Select chats and choose **Remove** (or press Delete) to take them out of Loop; they stay out on later imports, **Undo** brings them back, and original files are untouched.
2. Open **Settings** and choose sources, exclusions, schedule, notifications, and model connection.
3. On **Home**, click **Find opportunities**. Without a model, built-in CSV and Docker tools appear after three sessions that ask for that help. A passing mention does not count.
4. Open a solution, or choose **Details** to see evidence, give feedback, archive, revise, or restore a previous version.

Scheduled checks are opt-in and run every five minutes while Loop is running. Automatic suggestions have a weekly cooldown. Manual analysis bypasses the cooldown. Closing the window hides it to the tray where supported, and running work continues; use tray **Quit** to exit. **Pause** stops new imports and analysis and requests that active work stop. Filesystem calls to offline network folders may take time to return.

## Patterns

**Patterns** shows two steps.

1. **Your patterns** starts as a quick local keyword map, with nothing sent. **Analyze with AI** reads your requests in batches of up to 250, keeping similar ones together, using the model from Settings, after stating how much will be sent. The map grows by area as batches return, then settles into named patterns grouped from the short descriptions alone. Results are saved, so later runs read only new requests. Chats are always counted locally. **Focus on** is optional and steers grouping and findings. Click bubbles to choose what to analyze, or click a color to filter.
2. **What to do** sends the selection's statistics and up to 14 short excerpts per pattern (20 for the frustration pattern) to your model. It returns the problem, its root cause, and concrete suggestions (workflow, learn, automate, reference, habit, or tool), each with a first step. Findings must quote at least two provided messages. Reports are saved locally, and any suggestion can be saved to Home.

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

`desktop/ui-test/run.sh Drive` runs Loop's real window end to end from WSL with the Windows JDK: real controls, a throwaway home, and a fake local provider. It opens the window off-screen and draws only Loop's own components to images. `RealImport` reports counts from real transcripts without printing their text.
# recursive-human-intelligence
