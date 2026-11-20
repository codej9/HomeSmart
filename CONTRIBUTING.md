# Contributing

1. Branch off `main` and name the branch by its purpose, for example `feat/schedules` or `fix/door-label`.
2. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) style: a type and an optional scope, for example `feat(alerts): add door alerts` or `fix: keep the thermostat in range`.
3. Run `./mvnw verify` before you push. The MySQL tests need the `homesmart_test` database from the README.
4. Open a pull request into `main` and fill in the template. Merge it once CI passes.

## Git hooks

`lefthook.yml` checks the commit message type and whitespace, and runs `./mvnw -B verify` before each push.
To turn the hooks on, install lefthook (`brew install lefthook` or `winget install evilmartians.lefthook`) and run `lefthook install` once in your clone.
