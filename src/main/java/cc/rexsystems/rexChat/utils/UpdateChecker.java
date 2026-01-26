package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateChecker {
    private static final String SPIGET_ENDPOINT = "https://api.spiget.org/v2/resources/122562/versions/latest";
    private final RexChat plugin;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(RexChat plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdatesAsync() {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(SPIGET_ENDPOINT).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "RexChat-UpdateChecker");

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String json = sb.toString();
                    String latest = extractNameField(json);
                    this.latestVersion = latest;
                    if (latest != null && isNewerVersion(latest, plugin.getDescription().getVersion())) {
                        this.updateAvailable = true;
                        plugin.getLogUtils().info("A new version of RexChat is available: " + latest + " (current: " + plugin.getDescription().getVersion() + ")");
                        plugin.getLogUtils().info("Download: https://www.spigotmc.org/resources/rexchat.122562/");
                    } else {
                        this.updateAvailable = false;
                        plugin.getLogUtils().debug("RexChat is up to date (" + plugin.getDescription().getVersion() + ")");
                    }
                }
            } catch (Exception e) {
                plugin.getLogUtils().debug("Update check failed: " + e.getMessage());
            }
        });
    }

    private String extractNameField(String json) {
        // Very small JSON: {"id":...,"name":"1.1.0",...}
        int idx = json.indexOf("\"name\"");
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        int quoteStart = json.indexOf('"', colon + 1);
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteStart == -1 || quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd).trim();
    }

    private boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null) {
            return false;
        }
        
        // Treat empty strings as invalid (same as null)
        if (latest.trim().isEmpty() || current.trim().isEmpty()) {
            return false;
        }
        
        // If versions are identical, no update needed
        if (latest.equalsIgnoreCase(current)) {
            return false;
        }
        
        try {
            // Remove any non-numeric prefixes (like 'v') and suffixes (like '-SNAPSHOT')
            String cleanLatest = cleanVersionString(latest);
            String cleanCurrent = cleanVersionString(current);
            
            String[] l = cleanLatest.split("\\.");
            String[] c = cleanCurrent.split("\\.");
            int len = Math.max(l.length, c.length);
            
            for (int i = 0; i < len; i++) {
                int li = 0;
                int ci = 0;
                
                // Parse each segment, handling non-numeric parts gracefully
                if (i < l.length) {
                    li = parseVersionSegment(l[i]);
                }
                if (i < c.length) {
                    ci = parseVersionSegment(c[i]);
                }
                
                if (li > ci) return true;
                if (li < ci) return false;
            }
            return false;
        } catch (Exception e) {
            // If parsing completely fails, log and assume no update to avoid false positives
            plugin.getLogUtils().debug("Version comparison failed for '" + latest + "' vs '" + current + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clean version string by removing common prefixes and suffixes
     */
    private String cleanVersionString(String version) {
        if (version == null) return "0";
        
        // Remove 'v' or 'V' prefix
        version = version.replaceFirst("^[vV]", "");
        
        // Remove common suffixes like -SNAPSHOT, -RELEASE, -beta, etc.
        version = version.replaceAll("(?i)-(SNAPSHOT|RELEASE|BETA|ALPHA|RC\\d*).*$", "");
        
        return version.trim();
    }
    
    /**
     * Parse a version segment, extracting only the numeric part
     */
    private int parseVersionSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return 0;
        }
        
        // Extract leading digits only
        StringBuilder numericPart = new StringBuilder();
        for (char c : segment.toCharArray()) {
            if (Character.isDigit(c)) {
                numericPart.append(c);
            } else {
                break; // Stop at first non-digit
            }
        }
        
        if (numericPart.length() == 0) {
            return 0;
        }
        
        try {
            return Integer.parseInt(numericPart.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}


