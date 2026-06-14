package me.rexsystems.rexChat.service;

import me.rexsystems.rexChat.RexChat;
import me.rexsystems.rexChat.api.CustomChatToken;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for config-defined and plugin-provided custom chat tokens.
 */
public class CustomTokenRegistry {

    private final RexChat plugin;
    private final Map<String, CustomChatToken> byId = new ConcurrentHashMap<>();
    private final Map<String, CustomChatToken> byAlias = new ConcurrentHashMap<>();
    private final Map<String, Plugin> owners = new ConcurrentHashMap<>();

    public CustomTokenRegistry(RexChat plugin) {
        this.plugin = plugin;
    }

    public void reloadFromConfig() {
        byId.entrySet().removeIf(entry -> owners.get(entry.getKey()) == plugin);
        rebuildAliasIndex();

        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("chat-previews.custom-tokens");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection tokenSection = section.getConfigurationSection(key);
                if (tokenSection == null || !tokenSection.getBoolean("enabled", true)) {
                    continue;
                }
                registerInternal(plugin, new ConfigCustomChatToken(key, tokenSection));
            }
        }
        plugin.getChatColorManager().clearTokenPatternCache();
    }

    public void register(Plugin owner, CustomChatToken token) {
        if (token == null || token.getId() == null || token.getId().isBlank()) {
            throw new IllegalArgumentException("CustomChatToken id cannot be blank");
        }
        registerInternal(owner, token);
        plugin.getChatColorManager().clearTokenPatternCache();
    }

    public void unregister(String id) {
        CustomChatToken removed = byId.remove(id);
        owners.remove(id);
        if (removed != null) {
            for (String alias : removed.getAliases()) {
                byAlias.remove(alias.toLowerCase(Locale.ROOT));
            }
        }
        plugin.getChatColorManager().clearTokenPatternCache();
    }

    public CustomChatToken findByAlias(String alias) {
        if (alias == null) {
            return null;
        }
        return byAlias.get(alias.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomChatToken> getAll() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<String> getAllAliases() {
        return new ArrayList<>(byAlias.keySet());
    }

    private void registerInternal(Plugin owner, CustomChatToken token) {
        unregister(token.getId());
        byId.put(token.getId(), token);
        owners.put(token.getId(), owner);
        indexAliases(token);
    }

    private void indexAliases(CustomChatToken token) {
        for (String alias : token.getAliases()) {
            if (alias != null && !alias.isBlank()) {
                byAlias.put(alias.toLowerCase(Locale.ROOT), token);
            }
        }
    }

    private void rebuildAliasIndex() {
        byAlias.clear();
        byId.values().forEach(this::indexAliases);
    }
}
