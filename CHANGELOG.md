# Changelog

All notable changes to **RexChat** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.6] - 2026-06-14

### Added
- **`[coords]` / `[here]` location token.** Share your current position as a clickable label in chat. Anyone can click to copy coordinates; staff with `rexchat.coords.teleport` can run `/rexchat tpcoords <id>` to teleport to the shared location.
- **Custom chat token API.** Other plugins (and `chat-previews.custom-tokens` in config) can register clickable tokens with custom labels, hovers, and click actions (`open_url`, `run_command`, `suggest_command`, `copy_to_clipboard`). See `RexChatAPI#registerCustomToken`.
- **In-game container previews.** Shulker boxes, barrels, bundles, and decorated pots inside inventory/item preview GUIs can be clicked to inspect their stored contents (read-only). Discord `[item]` previews now include container contents for all supported container types.
- **In-game shulker box contents preview.** When viewing a player's inventory or held item via `[inventory]` / `[item]`, clicking a shulker box inside the read-only preview GUI opens a second GUI showing what's inside. The window title is the shulker's custom name, or its default material name if unnamed. Nested shulkers (a shulker inside another shulker) can be clicked through as well.
- **DiscordSRV — shulker contents for `[item]`.** When the held item is a shulker box, Discord now receives a second embed alongside the usual item card: a rendered PNG of the shulker GUI with its contents, titled with the shulker's name. Toggle with `chat-discord.previews.item-shulker-contents` (default: `true`); embed accent color via `chat-discord.embeds.item.shulker.color`.
- **DiscordSRV — rendered inventory & ender chest images.** When a chat message contains `[inv]` or `[ec]`, RexChat now renders an actual PNG that looks like the in-game GUI (light grey panel, recessed slots, real item textures, Monocraft font for stack counts) and posts it as a Discord attachment. Item textures are downloaded once from the configurable CDN (defaults to InventiveTalent's mirror) and cached under `<plugin folder>/textures/`.
- Bundled the Monocraft font (v4.2.1) under `resources/fonts/Monocraft.ttf` so counts render with the Minecraft typeface even on servers that don't have a Minecraft-style font installed.
- `chat-discord.images.texture-base-url` config option to point the renderer at a different texture mirror.
- DiscordSRV soft-dependency integration: when DiscordSRV is installed, chat messages containing `[item]`, `[inv]`, `[ec]` or `[bal]` tokens are forwarded to the linked Discord channel as the regular chat line **plus** rich embeds for the previews. Items get a thumbnail (configurable URL template, defaults to InventiveTalent's Minecraft asset mirror), durability, enchantments and lore. Balances are inlined directly in the relayed line via Vault Economy.
- `chat-discord` config section with toggles per preview type, channel selection (DSRV game-channel name, empty = main channel), embed colors and titles.

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

[Unreleased]: https://github.com/rexsystems/RexChat/compare/v1.6.6...HEAD
[1.6.6]: https://github.com/rexsystems/RexChat/releases/tag/v1.6.6
[1.6.5]: https://github.com/rexsystems/RexChat/releases/tag/v1.6.5
