<div align="center">

# RHI

### Recursive Human Intelligence

**AI improves itself recursively. Why shouldn't you?**

One window for every coding agent you run (Claude Code, Codex, the ChatGPT app, Pi), then honest feedback on how you work with them.

![RHI: every agent on one dashboard, then feedback from your own chats](docs/demo.gif)

<sub>Demo with a fictional user, made-up chats, and scripted output.</sub>

</div>

## Install

No Java needed. The dashboard works without an AI key; feedback uses your own OpenAI, Claude, or OpenRouter key.

```sh
# macOS and Linux
curl -fsSL https://raw.githubusercontent.com/andrearitossa/recursive-human-intelligence/main/install.sh | sh
```

```powershell
# Windows (PowerShell)
irm https://raw.githubusercontent.com/andrearitossa/recursive-human-intelligence/main/install.ps1 | iex
```

Builds aren't code signed yet, so your system may warn you. The scripts are short, and they install only for your user: read [install.sh](install.sh) or [install.ps1](install.ps1) first. Run the same command again to update.

## See every agent at a glance

![The dashboard: what needs you, what is working, every project on one timeline](docs/dashboard.png)

The **Dashboard** opens first. It reads your agents' session logs on this computer and refreshes every ten seconds.

- **What needs you comes first.** Sessions waiting for an answer or stopped mid-task sit at the top, then whatever is working now, then the rest of your projects and chats.
- **One timeline per project.** Every turn of every session on one bar, colored by agent: orange for Claude, blue for Codex and ChatGPT, green for Pi. Hover for the prompt, click to open it.
- **Pin what you are watching.** Pinned projects get their own row above the filter, with live status. Right-click one to rename, move, or unpin it.
- **Jump straight back in.** Open in ChatGPT or the Claude app, find the terminal a session runs in, resume it, or open its folder in VS Code.
- **Filter by agent and time.** Chips for each app, a search across project and session names, and 24 hours to all time.

![A session in the sidebar, with Markdown rendered](docs/session.png)

**Click any card** and the whole session opens in a sidebar, with Markdown, code, and tables rendered. Esc closes it.

| Agent | Where it runs |
|---|---|
| Claude Code | Terminal, the Claude desktop app, VS Code |
| Codex | Terminal, VS Code, the ChatGPT desktop app |
| Pi | Terminal |

Live status (working, waiting for you) comes from running processes on Windows and WSL. Elsewhere, Codex and Pi show as working from recent activity.

## Then get feedback

Click **Run feedback loop**. It's the feedback a blunt friend would give after reading every request you ever typed:

- **Your habits, with receipts.** "You've typed this phrase in 23 chats. Save it once, with real limits." It shows what you wrote and a sharper version.
- **What to actually learn.** Keep asking about the same subject? It names a real book or course, instead of answering again.
- **What you keep reopening.** Decisions you never settle, and questions you're asking an AI that only customers can answer.

Every item quotes your own messages, so you can check it.

<img src="docs/patterns.png" width="49%" alt="Pattern map"> <img src="docs/feedback.png" width="49%" alt="Feedback list">

1. **Run it once.** Your requests are read and grouped into recurring behaviors, live, on **Patterns**.
2. **Act on what arrives.** Open an item on **Feedback**, do the one thing it asks, and mark it done.
3. **Come back.** New chats unlock new feedback, and a weekly check can run on its own.

## What leaves your computer

| | |
|---|---|
| **Stays local** | Your transcripts, which are never changed, and everything RHI stores in `~/.loop`. The Dashboard never sends anything |
| **Sent, after you confirm** | For feedback: your requests, cut to 1,000 characters each, to the provider you chose, with your key. For **Name projects with AI**: folder names and up to 8 session titles per project, never transcripts |
| **Removed first** | Obvious credentials. Names and other personal details in your messages can remain, so remove sensitive chats or skip project folders first |
| **Your API key** | Encrypted by Windows, macOS Keychain, or the Linux keyring. Without one, it stays in memory for that session |

RHI also looks in other profiles you can access: WSL home folders when running on Windows, and folders under `C:\Users` when running inside WSL. Every location is listed, and editable, in **Settings → Chat locations**: add a path, remove one, or restore the standard ones. Dashboard, Chats, and feedback all read that one list.

<details>
<summary><b>Details: providers, patterns, files, building from source</b></summary>

### Use it

Click **Run feedback loop** on the Dashboard, or **Get feedback** on the Feedback page. One click is a complete read: the agent keeps looking, up to ten passes, until nothing strong is left. Feedback shows which pass is running, and **One more pass** asks for another whenever you want. The main button then becomes **Check for new chats**, which calls the model only if your chats changed. That one button does everything in order: asks for your AI connection if it is missing, checks for new chats, reads and groups what is new, and writes your feedback. Items appear as they arrive, and you can open them while it is still working.

Every Sunday at 16:00 on your computer's clock, while RHI is running, it reads new chats, gets feedback, and sends a desktop notification. If RHI was closed, this happens soon after the next launch. This sends excerpts to your provider without asking each time, and only once you have connected a model and allowed sending. Turn it off in **Settings → Background**.

**Patterns** and **Chats** are there to explore and to narrow things down; you never need them. The **>** at the bottom opens logs of each step: API calls with sizes, timing, and tokens, what each batch produced, each pattern and item as it lands, and anything dropped and why. Logs show model-written names, never keys or your messages.

### Patterns

1. **Patterns** starts as a quick local keyword map, with nothing sent. **Analyze with AI** reads your requests in batches of up to 250, keeping similar ones together, using the model from Settings, after stating how much will be sent. The map grows by area as batches return, then settles into named patterns grouped from the short descriptions alone. Results are saved, so later runs read and group only new requests, placing them into your existing patterns. **Regroup** rebuilds the map from everything. Chats are always counted locally. **Focus on** is optional and steers grouping and findings. Click bubbles to choose what to analyze, or click a color to filter.
2. **Get feedback**, on Feedback or Patterns, opens Feedback and sends the one-line description of every request, plus excerpts from your patterns, to your model. It returns a short list of named items. Click one for the detail, what to do, and a search for any source it names. Items must quote at least two real messages. Phrases you retype across chats are measured locally and shown to the agent. **Mark as done** moves an item to a hidden list below, where **Undo** brings it back, and it will not be suggested again. **Remove**, on done items and in the detail view, forgets an item so it can come back.

### Connect a model

Settings supports three providers through one model interface:

| Provider | Default endpoint | Environment key |
|---|---|---|
| OpenAI | `https://api.openai.com/v1/chat/completions` | `OPENAI_API_KEY` |
| Claude | `https://api.anthropic.com/v1/messages` | `ANTHROPIC_API_KEY` |
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions` | `OPENROUTER_API_KEY` |

Enter a model ID available to your account. Paste a key or set the environment variable. Keys are never written to settings. **Forget saved key** in Settings removes a stored key. Click **Test connection** and enable consent to send selected excerpts. Settings save themselves. Testing sends a small test prompt, not chat history. Custom endpoints require HTTPS, except localhost development endpoints.

Analysis samples at most 80 user excerpts / 45,000 text characters per call. Obvious credentials are redacted and session names replaced with anonymous codes before sending. Evidence must reference actual sampled requests from three distinct sessions. The app rejects invalid output and unsupported operations. Archived topics and similar titles are suppressed. Feedback and generated content are stored separately and are never imported as evidence.

### Files and generated tools

History and state live in `~/.loop/`: `history.tsv`, `scan-index.tsv`, `settings.properties`, `solutions.json`, generated HTML artifacts, and the Dashboard's `agents-index.json` (a read cache, so later launches take seconds), `agents-pins.tsv`, and `agents-names.properties`. Unchanged files are not reparsed. Transcripts are streamed, up to 512 MB each. Only what the user typed is kept: injected context, slash commands, tool results, meta turns, and subagent sessions are dropped. No directory symlinks are followed. Windows discovers WSL homes; WSL checks Windows profiles. Environment location overrides are supported; `LOOP_WINDOWS_HOME` adds an explicit profile.

Generated HTML opens in your browser. It uses a sandboxed iframe, restrictive content policy, and an application-owned renderer. The model supplies plain content, quiz data, and allowlisted text operations (trim, remove empty lines, deduplicate, literal replace), never executable JavaScript. The CSV cleaner accepts a dropped or chosen file, recognizes comma, semicolon, and tab delimiters and UTF-8 or ANSI text, shows a preview, and saves a copy. Previous solution versions and explicit feedback remain available in the desktop library.

Exclude a project by its real folder, for example `/home/you/client-work`. Exclusions affect future imports. Previously imported history is retained until deletion and may still be used in analysis. Delete history removes imported requests and solution data, leaving original chat transcripts untouched. Cloud sync, signed installers, arbitrary scripts, and an MCP server are not included.

### Find saved solutions from an agent harness

After compiling, any harness with access to Java can look up active solutions:

```sh
java -cp desktop/build Loop --search "docker"
```

The command returns JSON with title, description, version, and a standalone artifact path. Archived tools are excluded; it performs no model calls and does not launch the desktop window.

### Validation

```sh
./desktop/check.sh
```

The Windows launcher runs the same checks: transcript formats, harness-text filtering, project exclusions, deduplication, cancellation, persistence, provider formats, evidence validation, cooldowns, feedback, revision and restore, CSV delimiters and encodings, redaction, and artifact escaping.

`desktop/ui-test/run.sh Drive` runs RHI's real window end to end from WSL with the Windows JDK: real controls, a throwaway home, and a fake local provider. It opens the window off-screen and draws only RHI's own components to images.

### Build from source

Requires JDK 17+. `desktop/start.bat` or `desktop/start.sh` compiles, runs checks, and opens the app. `desktop/package.sh NAME` builds a self-contained package. Pushing a `v*` tag builds and publishes releases for every platform.

</details>

## Contributing

All you need is JDK 17. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE). Free to use, change, and sell. Copies and derived works must carry the credit to **Andrea Ritossa** in [NOTICE](NOTICE). If this project inspires your own work, please credit it too.
