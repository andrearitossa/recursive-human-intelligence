# RHI desktop app

## Current implementation

RHI is a Java desktop app using the JDK standard library (Swing). It runs on Windows or Linux/WSL with JDK 17+. This replaces the earlier Electron and browser-only approaches, which could not provide dependable automatic filesystem access in Brave. The earlier HTML prototype remains in `app/`.

The model interface supports **OpenAI, Claude, and OpenRouter**. Provider, endpoint, model ID, and credentials are configurable. The product logic does not depend on one model vendor. Keys are kept in memory or loaded from provider environment variables; chat excerpts are sent only when the model connection consent is enabled.

Implemented and exercised on Windows with JDK 25:

- Native import with live per-folder counts, Windows/WSL discovery, cancellation, persisted history, deduplication, and an incremental file index.
- Home/library, Chats (per-chat list with remove and undo), and self-saving Settings tabs; searchable active and archived solutions.
- Filtering of harness-injected text and subagent sessions, so only what the user typed counts as evidence.
- Drag-and-drop CSV cleaner for comma, semicolon, and tab files in UTF-8 or ANSI.
- Patterns screen: local interaction and topic clustering shown as a bubble map, then model-written findings with evidence and suggestions.
- Explicit manual opportunity analysis plus optional five-minute scheduled imports and analysis.
- Provider adapters, connection test, bounded evidence sampling, three-session evidence checks, and rejection of invented evidence.
- Curated tools without a model; model-backed references, interactive lessons, and deterministic text tools.
- Sandboxed standalone HTML artifacts rendered from validated data, with no model-supplied executable code or credential access.
- Evidence details, Useful and Prefer delegating feedback, model-backed improvement, retained versions, and restore as a new version.
- Pause/resume, source toggles, project-folder exclusions for future imports, and added-folder management.
- Tray Open/Pause/Quit, close to tray where supported, and optional desktop notifications.
- Weekly automatic suggestion cooldown, archived-topic suppression, similar-title filtering, and separation of generated solutions from source transcripts.
- Delete imported history, index, solution metadata, feedback, and generated artifact files. Original transcripts stay untouched.

The Windows launcher finds a JDK 17+, compiles all Java sources, and runs local checks before opening the window. `desktop/ui-test` drives the real window end to end with a fake local provider, and generated tools were exercised in headless Chromium. The quality of real model findings has not been evaluated. Requests to the real OpenAI, Claude, and OpenRouter services have not been tested, because they require the user's credentials. `start.sh` has not been run, because this WSL has no JDK.

## Current limits

- Persistence uses atomic local files rather than SQLite. There is no account, cloud sync, or signed installer.
- Scheduled work requires RHI to stay running. Source exclusions apply to future imports; existing history remains until deleted.
- Model analysis considers bounded batches, not every transcript in a single request. It cannot guarantee that every recurring workflow will be identified.
- Generated transformations use an allowlist of deterministic text operations. Arbitrary generated scripts and automatic external actions are outside this implementation.
- A provider-neutral `--search` CLI exposes saved solutions to agent harnesses. There is no MCP server or automatic next-session recommendation hook.

## Product idea

RHI is a simple desktop companion for agentic tools such as Claude Code, Codex, Pi, and other harnesses. It notices recurring tasks and friction in a user's interactions, then helps create a durable solution: a reference, a tailored lesson, a reusable workflow, a script, or a small interactive tool. The goal is to empower users over time, save repeated effort, and make useful solutions easy to find again.

Examples include a personalized Docker reference or lesson when a user repeatedly asks for Docker help, a reusable email editing tool for recurring email work, or a script that automates repeated spreadsheet cleanup. Repetition alone does not prove that a user wants to learn; RHI should distinguish between helping someone learn, helping them remember, and doing the task for them.

## Product shape

Build a quiet desktop app with a tray or menu-bar presence, native notifications, a local solution library, and settings for connected harnesses and data access. Keep the analysis engine separate from the desktop interface so integrations and interfaces can grow over time. Skills and harness integrations can help agents retrieve and use solutions; the desktop service owns persistent state and the background loop.

```text
Harness adapters → Local history → Opportunity analysis
                                      ↓
                             Build and validate a solution
                                      ↓
                       Library, notification, and feedback
                                      ↺
```

## User experience

1. The user connects a harness and chooses which projects or conversations RHI may use.
2. RHI reviews new interactions in the background and identifies recurring tasks or friction.
3. When there is a useful opportunity, RHI prepares and validates a small solution, then notifies the user.
4. The user opens and uses the solution, finds it later in the library, and can give feedback or request an improvement.
5. RHI uses feedback and outcomes to improve, retire, or stop suggesting solutions.

The app has three main screens:

- **Home:** a featured new solution and saved tools. With no solutions yet, show: “Keep working. RHI will look for ways to make it easier.”
- **Tool:** the solution, a short explanation of why it was created, and **Improve**, **Useful**, and **Archive** actions.
- **Settings:** connected harnesses, included projects, model connection, pause collection, and delete history.

The tray menu offers **Open RHI**, **Pause**, and **Quit**. Notifications should be specific and respectful, for example: “You’ve cleaned up this report format a few times. Your report cleaner is ready.”

## Product principles

- Choose the smallest useful intervention; do not default to generating an HTML page or course.
- Build a working draft and validate it before saying it is ready.
- Stay selective. Begin with a threshold such as three similar requests across separate sessions, plus evidence that a solution would help.
- Limit proactive suggestions (initially at most one per week), respect dismissals, and avoid repeated near-duplicate tools.
- Do not treat opening a tool or asking fewer questions as proof of success. Use explicit feedback and useful outcomes such as task completion, fewer corrections, or reported time saved.
- Keep system-generated suggestions and output out of the evidence used to infer user demand.
- Give users clear controls to choose sources, pause collection, and delete history. Start with explicitly selected sources and local storage.

## First version

Start with one operating system and one harness integration. Use a local database (SQLite is a suitable starting point) for imported interaction references, detected patterns, solution versions, and feedback. Analyze only new interactions on a schedule, and provide a manual **Find opportunities now** action during development and onboarding.

Support two solution formats initially:

1. Interactive references or lessons.
2. Small tools that transform user-selected files.

Include native notifications, a searchable solution library, feedback, versioning, archive, and suggestion cooldowns. A CLI may help with development and integrations, while the desktop app is the primary user experience. A web library, cloud sync, accounts, marketplace, mobile apps, and multiple collaborating agents can follow after the core loop proves useful.

Generated tools must run with limited permissions. For example, an interactive HTML tool should not have direct access to desktop internals, credentials, or the filesystem; file access should be granted explicitly for the task. If a generated tool needs model access, provide it through a service rather than embedding credentials in the page.

## Milestones

| Milestone | Deliverable | Completion signal |
|---|---|---|
| 1. Desktop shell | Tray app, home, settings, and local database | Users can open, pause, quit, and retain settings. |
| 2. Observe | One harness adapter, incremental import, opportunity detection | A recurring task is identified with references to its source interactions and no duplicate imports. |
| 3. Deliver | Generate, validate, display, and notify about a solution | A real repeated task can be completed with the generated tool. |
| 4. Improve | Feedback, versioning, archive, and cooldowns | Feedback can improve a solution while preserving its prior working version. |

## First demonstration

Use a small set of conversations about repeatedly cleaning the same CSV format. RHI identifies the recurring task, creates and validates a drag-and-drop cleaner, notifies the user, and successfully transforms a new file. The user can rate it, request a change, or archive it.

## Success criterion

Users return to a RHI solution because it helps them finish their work. The first release should demonstrate one recurring task made measurably easier and a feedback loop that improves the solution based on user input.
