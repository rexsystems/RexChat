<p align="center">
  <a href="https://rexsystems.me">
    <img src="https://cdn.694206767.xyz/rexchat/banner.png" alt="RexChat — modern chat for Minecraft servers" width="100%">
  </a>
</p>

<p align="center">
  <strong>Modern chat for survival, SMP & network servers.</strong><br>
  Formatted messages, clickable previews, mentions, moderation tools & DiscordSRV integration — all in one plugin.
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/rexchat.122562/"><img src="https://img.shields.io/badge/Spigot-Download-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white" alt="Spigot"></a>
  <a href="https://modrinth.com/plugin/rexchat"><img src="https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth"></a>
  <a href="https://rexsystems.me"><img src="https://img.shields.io/badge/Website-rexsystems.me-5865F2?style=for-the-badge" alt="Website"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.20.4%2B-62B47A?style=for-the-badge" alt="Minecraft 1.20.4+">
  <img src="https://img.shields.io/badge/Java-21-blue?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-supported-FCD34D?style=flat-square" alt="Paper">
  <img src="https://img.shields.io/badge/Purpur-supported-FCD34D?style=flat-square" alt="Purpur">
  <img src="https://img.shields.io/badge/Folia-supported-FCD34D?style=flat-square" alt="Folia">
  <img src="https://img.shields.io/badge/PlaceholderAPI-optional-EBBCBC?style=flat-square" alt="PlaceholderAPI">
  <img src="https://img.shields.io/badge/Vault-optional-EBBCBC?style=flat-square" alt="Vault">
  <img src="https://img.shields.io/badge/DiscordSRV-optional-EBBCBC?style=flat-square" alt="DiscordSRV">
</p>

---

<p align="center">
  <img src="https://cdn.694206767.xyz/rexchat/features.png" alt="RexChat features overview" width="900">
</p>

## Why RexChat?

RexChat replaces plain Minecraft chat with something your players actually enjoy using. Per-group formats, hover cards, `@mentions`, emoji shortcuts, and **clickable chat previews** — share an item, inventory, coords, or balance without spamming screenshots.

Staff get fast moderation (`/clearchat`, `/mutechat`), optional proximity chat, and full control through simple YAML config. Hook up DiscordSRV and your in-game previews become rich embeds with real Minecraft-style images in Discord.

---

## Highlights

| Chat & formatting | Previews & tokens | Moderation & extras |
|---|---|---|
| Per-group chat formats | `[item]` — held item preview | `/clearchat` & `/mutechat` |
| Player hover tooltips | `[inventory]` — full inventory GUI | Proximity / local chat |
| `@mentions` + title + sound | `[ec]` — ender chest preview | Join & leave messages |
| `/chatcolor` presets | `[bal]` — Vault balance (hover) | Chat reporting bypass (1.19+) |
| Emoji aliases (`:)`, `<3`, …) | `[coords]` / `[here]` — copy location | Custom info commands |
| MiniMessage & hex colors | Container inspect (shulker, barrel, bundle, pot) | Developer API |
| PlaceholderAPI support | Custom tokens (config + API) | DiscordSRV image embeds |

---

## Chat preview tokens

Type these in chat — they expand into clickable components for everyone:

| Token | What it does |
|---|---|
| `[item]` / `[i]` | Shows your held item; click to open a read-only preview |
| `[inventory]` / `[inv]` | Opens a snapshot of your inventory |
| `[ec]` / `[enderchest]` | Opens your ender chest |
| `[bal]` / `[balance]` | Shows your Vault balance (requires Economy plugin) |
| `[coords]` / `[here]` | Shares your position; click to **copy coordinates** |

**Container previews:** inside any item/inventory preview GUI, click a shulker box, barrel, bundle, or decorated pot to inspect what's stored inside.

**Staff:** `/rexchat tpcoords <id>` teleports to a shared `[coords]` location (permission: `rexchat.coords.teleport`).

---

## DiscordSRV integration

When DiscordSRV is installed, RexChat automatically enhances relayed chat:

- **`[item]`** — item icon + Minecraft-style tooltip card; containers also get a contents image
- **`[inv]`** — rendered inventory PNG (real GUI chrome + item textures)
- **`[ec]`** — rendered ender chest PNG
- **`[bal]`** — balance inlined in the Discord message

Toggle each preview under `chat-discord` in `config.yml`. Textures are cached locally after first download.

---

## Installation

1. Download the latest JAR from [Spigot](https://www.spigotmc.org/resources/rexchat.122562/) or [Modrinth](https://modrinth.com/plugin/rexchat).
2. Drop it into your server's `plugins/` folder.
3. Restart the server (or use a plugin manager).
4. Edit `plugins/RexChat/config.yml` — missing keys are merged automatically on reload.
5. Run `/rexchat reload` after changes.

**Requirements:** Paper / Purpur / Folia **1.20.4+**, **Java 21**.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/rexchat reload` | Reload configuration | `rexchat.admin` |
| `/rexchat help` | Show help | `rexchat.admin` |
| `/rexchat global` / `/local` | Toggle proximity bypass | `rexchat.proximity.toggle` |
| `/clearchat` (`/cc`) | Clear chat for everyone | `rexchat.clear` |
| `/mutechat` (`/mc`) | Toggle global chat mute | `rexchat.mute` |
| `/chatcolor` (`/color`) | Pick a chat color preset | `rexchat.chatcolor` |

---

## Permissions (essentials)

| Permission | Default | Description |
|---|---|---|
| `rexchat.preview` | `true` | Use chat preview tokens |
| `rexchat.preview.coords` | `true` | Share coordinates via `[coords]` / `[here]` |
| `rexchat.coords.teleport` | `op` | Teleport to shared coords |
| `rexchat.chatcolor` | `op` | Use color codes in chat |
| `rexchat.bypass` | `op` | Write while chat is muted |
| `rexchat.admin` | `op` | Full admin access |

See `plugin.yml` for the full permission list (`rexchat.preview.item`, `rexchat.preview.inv`, group-specific chat colors, etc.).

---

## PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%rexchat_muted%` | Whether chat is muted |
| `%rexchat_chatcolor%` | Selected color display name |
| `%rexchat_chatcolor_raw%` | Raw color id |
| `%rexchat_chatcolor_format%` | Format string (`&c`, `<rainbow>`, …) |

---

## Custom tokens (API)

Register your own clickable chat tokens from another plugin:

```java
RexChatAPI api = RexChatAPI.getInstance(myPlugin);
api.registerCustomToken(myPlugin, new CustomChatToken() {
    @Override public String getId() { return "store"; }
    @Override public Collection<String> getAliases() { return List.of("[store]"); }
    @Override
    public Component buildReplacement(Player sender, String matchedToken) {
        return Component.text("[Store]")
            .clickEvent(ClickEvent.openUrl("https://store.example.com"));
    }
});
```

Or define tokens entirely in config under `chat-previews.custom-tokens` (labels, hovers, click actions — no code required).

---

## Links

- **Website:** [rexsystems.me](https://rexsystems.me)
- **Spigot:** [rexchat.122562](https://www.spigotmc.org/resources/rexchat.122562/)
- **Modrinth:** [rexchat](https://modrinth.com/plugin/rexchat)
- **Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## License

RexChat is licensed under the **GNU Affero General Public License v3.0**. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

<p align="center">
  <sub>Made with ☕ by <a href="https://rexsystems.me">RexSystems</a></sub>
</p>
