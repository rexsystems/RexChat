# Changelog

All notable changes to **RexChat** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

<!-- Add entries here as you work. They will be moved into the next versioned section on release. -->

## [1.6.5] - 2026-05-10

### Added
- New `[bal]` / `[balance]` / `[money]` chat preview tokens (and `{bal}`, `{balance}`, `{money}` variants) that show the sender's economy balance via Vault, with hover tooltip showing the full amount.
- `messages.preview.balance` config section with `label-template`, `hover` and `unavailable-label` (placeholders: `{balance}`, `{amount}`, `{currency}`, `{player}`).
- Dedicated ender chest preview support: `[ec]` / `[enderchest]` / `[echest]` tokens (and curly variants) open a preview of the sender's ender chest on click.
- Nightly build GitHub Actions workflow that publishes a rolling `nightly` pre-release with the latest jar on every push to `main`.
- Release GitHub Actions workflow that, on every `v*` tag, builds the jar, creates a GitHub Release, and publishes the version to Modrinth (paper, purpur, folia, bukkit, spigot — Minecraft 1.20.4+).

### Fixed
- `[ec]` / `[enderchest]` tokens were not being replaced when the sender had a chatcolor preset selected — the color code was being prepended to the token, breaking literal matching. Balance and ender chest tokens are now also excluded from chatcolor application.
- Emoji aliases like `:)` and `<3` were also matching when used inside longer sequences (e.g. `:))`, `<33`). Aliases now only match as standalone tokens (surrounded by whitespace, start, or end of the message).
- `rexchat.preview.enderchest` permission was missing from `plugin.yml`.
- Mention highlighting could break message colors and was sometimes missing the `@` symbol.
- Player chat color is now restored after a mention highlight instead of being reset to white.

## Older versions

For changes prior to 1.6.5, see the [GitHub Releases page](https://github.com/rexsystems/RexChat/releases).

[Unreleased]: https://github.com/rexsystems/RexChat/compare/v1.6.5...HEAD
[1.6.5]: https://github.com/rexsystems/RexChat/releases/tag/v1.6.5
