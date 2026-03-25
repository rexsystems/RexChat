# RexChat

RexChat is a modern chat management plugin designed for survival, SMP and network servers that want a clean, customizable and user-friendly chat experience.

It replaces the default Minecraft chat with formatted messages, colors, mentions, emojis, item previews and quality-of-life tools for both players and staff. With RexChat you can fully control how your chat looks for each group, disable annoying chat reports, let players choose their own chat color, and keep everything clean with powerful clear and mute commands.

Everything is configured through simple YAML files, so you can quickly adapt the plugin to any server style – from small survival communities to larger networks.

---

## ✨ Main Features

- Per-group chat formatting with support for prefixes, hover info and placeholders  
- ClearChat and MuteChat commands for fast moderation  
- Custom join and leave messages  
- Mention system with on-screen title and chat highlight  
- Chat emojis and replacements for common text  
- Player-selectable chat colors using `/chatcolor`  
- Chat reporting disabler for modern Minecraft versions  
- Folia, Paper and Purpur support, optimized for 1.20.4+  

---

## 🔧 Requirements

We only provide support for versions 1.20.4 and above, versions below that should work but it's not guaranteed.

---

## 🔒 Permissions

- `rexchat.admin` - Access to all commands (reloading, muting, clearing)  
- `rexchat.mutechat` - Allows muting the chat  
- `rexchat.clearchat` - Allows clearing the chat  
- `rexchat.bypass` - Allows writing while chat is disabled  
- `rexchat.chatcolor` - Allows using colors in messages  

---

## ⚙️ Placeholders

- `%rexchat_muted%` — Returns true or false if chat is muted  
- `%rexchat_chatcolor%` — Returns display name (e.g. "Red", "Gold")  
- `%rexchat_chatcolor_raw%` — Returns raw name (e.g. "red", "gold")  
- `%rexchat_chatcolor_format%` — Returns format (e.g. "&c", "<rainbow>")  

---
