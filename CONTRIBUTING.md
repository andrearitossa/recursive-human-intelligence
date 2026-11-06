# Contributing

All you need is JDK 17 or newer.

```sh
./desktop/start.sh    # Run the app (Windows: desktop\start.bat)
./desktop/check.sh    # Run the tests
```

Then change something and open a pull request. No setup, no dependencies, and no change is too small.

Not sure where to start?

- **Improve the feedback:** edit `INSTRUCTIONS` in `desktop/Insights.java`.
- **Add a harness:** add its folder to `addHome` in `desktop/ChatEngine.java`, and its log format to `apply` in `desktop/Agents.java` so it shows on the Dashboard.
- **Polish the Dashboard:** it lives in `desktop/AgentsPage.java`. `desktop/ui-test/Demo.java` re-records the README media with a fictional user.
- **Report a bug or idea:** open an issue.

Please never share real chats or API keys in issues or pull requests.
