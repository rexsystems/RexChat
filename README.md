# RexChat

A modern chat management plugin for Minecraft servers running versions 1.17 through 1.21.4.

## What it does

RexChat gives you control over your server's chat with essential moderation tools and customizable formatting. It's built to be lightweight and straightforward - no bloat, no unnecessary features.

### Core Features

- **Chat Control** - Mute or clear chat when needed, with bypass permissions for staff
- **Custom Commands** - Define your own commands through the config file
- **Chat Formatting** - Customize how messages appear with full color support (including HEX colors on 1.16+)
- **Player Tooltips** - Hovering over a player's name shows health, location, world, and ping
- **PlaceholderAPI Support** - Works with PAPI placeholders out of the box

## Requirements

- Java 21+
- Minecraft 1.17 - 1.21.4
- Paper or Spigot server

## Installation

1. Drop the plugin jar into your `plugins` folder
2. Restart the server
3. Configure settings in `plugins/RexChat/config.yml`
4. Reload with `/rc reload`

## Commands & Permissions

| Command | Aliases | Permission | What it does |
|---------|---------|------------|--------------|
| `/rexchat` | `/rc` | `rexchat.admin` | Main command and reload |
| `/mutechat` | `/mc` | `rexchat.mute` | Toggle chat mute |
| `/clearchat` | `/cc` | `rexchat.clear` | Clear chat messages |

Additional permissions:
- `rexchat.bypass` - Talk when chat is muted

## Configuration

The config file is pretty self-explanatory. You can customize messages, chat format, hover tooltips, and create custom commands. 

Example chat format setup:
```yaml
chat-format:
  enabled: true
  format: "&7{player}&8: &f{message}"
  player:
    hover:
      enabled: true
      lines:
        - "&cHealth: {health}/{max_health}"
        - "&eWorld: {world}"
        - "&bLocation: {x}, {y}, {z}"
        - "&aPing: {ping}ms"
```

Available placeholders: `{player}`, `{display_name}`, `{message}`, `{world}`, `{health}`, `{max_health}`, `{x}`, `{y}`, `{z}`, `{ping}`

## Building from Source

```bash
git clone https://github.com/rexsystems/RexChat.git
cd RexChat
mvn clean package
```

The compiled jar will be in the `target` folder.

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Links

- [Releases](https://github.com/rexsystems/RexChat/releases)
- [Issues](https://github.com/rexsystems/RexChat/issues)

---

Made by RexSystems
