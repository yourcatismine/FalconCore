package com.falconcore.survival.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Automatic YAML Configuration Updater.
 * Dynamically detects and extracts all YAML files inside the plugin's resources folder
 * (excluding plugin.yml), deeply merges new keys, and safely injects them without
 * overwriting existing user-customized values.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
        // Utility class
    }

    /**
     * Automatically discovers and updates all YAML configuration files inside the plugin resources.
     *
     * @param plugin the JavaPlugin instance
     * @return total count of new keys injected across all configuration files
     */
    public static int updateAll(JavaPlugin plugin) {
        Set<String> resourcePaths = new HashSet<>(detectAllYamlResources(plugin));

        int totalInjected = 0;
        for (String resourcePath : resourcePaths) {
            try {
                int injected = update(plugin, resourcePath);
                if (injected > 0) {
                    totalInjected += injected;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ConfigUpdater] Failed to process auto-update for '" + resourcePath + "': " + e.getMessage());
            }
        }

        if (totalInjected > 0) {
            plugin.getLogger().info("[ConfigUpdater] Successfully injected " + totalInjected + " new configuration key(s) across active configs.");
        }

        return totalInjected;
    }

    /**
     * Updates a single YAML configuration file against its embedded default resource.
     *
     * @param plugin the JavaPlugin instance
     * @param resourcePath the relative path to the resource (e.g. "survival/config.yml")
     * @return number of new keys injected into this configuration file
     */
    public static int update(JavaPlugin plugin, String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return 0;
        }

        // Normalize slashes
        resourcePath = resourcePath.replace('\\', '/');
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        File targetFile = new File(plugin.getDataFolder(), resourcePath);

        // If file does not exist on disk, save the default resource directly
        if (!targetFile.exists()) {
            targetFile.getParentFile().mkdirs();
            try {
                if (plugin.getResource(resourcePath) != null) {
                    plugin.saveResource(resourcePath, false);
                    return 0;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ConfigUpdater] Could not save initial resource: " + resourcePath + " (" + e.getMessage() + ")");
            }
            return 0;
        }

        // File exists -> compare active against embedded defaults
        InputStream resourceStream = plugin.getResource(resourcePath);
        if (resourceStream == null) {
            return 0; // No embedded default resource to compare against
        }

        try (InputStreamReader reader = new InputStreamReader(resourceStream, StandardCharsets.UTF_8)) {
            FileConfiguration activeConfig = YamlConfiguration.loadConfiguration(targetFile);
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);

            int injectedKeys = mergeConfigurations(activeConfig, defaultConfig);

            if (injectedKeys > 0) {
                activeConfig.save(targetFile);
                plugin.getLogger().info("[ConfigUpdater] Injected " + injectedKeys + " missing key(s) into " + resourcePath);
            }

            return injectedKeys;
        } catch (Exception e) {
            plugin.getLogger().warning("[ConfigUpdater] Error updating '" + resourcePath + "': " + e.getMessage());
            return 0;
        }
    }

    /**
     * Recursively injects missing keys from defaultConfig into activeConfig without modifying existing values.
     *
     * @param activeConfig the active server configuration
     * @param defaultConfig the default JAR configuration
     * @return number of keys newly injected
     */
    public static int mergeConfigurations(FileConfiguration activeConfig, FileConfiguration defaultConfig) {
        int injectedCount = 0;

        for (String key : defaultConfig.getKeys(true)) {
            if (!activeConfig.contains(key)) {
                if (defaultConfig.isConfigurationSection(key)) {
                    activeConfig.createSection(key);
                } else if (defaultConfig.isList(key)) {
                    activeConfig.set(key, defaultConfig.getList(key));
                    injectedCount++;
                } else {
                    activeConfig.set(key, defaultConfig.get(key));
                    injectedCount++;
                }

                // Copy comments if available
                try {
                    List<String> comments = defaultConfig.getComments(key);
                    if (comments != null && !comments.isEmpty()) {
                        activeConfig.setComments(key, comments);
                    }
                    List<String> inlineComments = defaultConfig.getInlineComments(key);
                    if (inlineComments != null && !inlineComments.isEmpty()) {
                        activeConfig.setInlineComments(key, inlineComments);
                    }
                } catch (Throwable ignored) {
                    // Method may not exist on legacy platforms
                }
            } else if (defaultConfig.isList(key) && activeConfig.isList(key)) {
                // Key exists in both and is a List (e.g. bad-words, command lists, etc.)
                // Deep merge list elements: add any newly added default items that are missing from active
                List<?> defaultList = defaultConfig.getList(key);
                List<?> activeList = activeConfig.getList(key);
                if (defaultList != null && activeList != null) {
                    List<Object> mergedList = new ArrayList<>(activeList);
                    boolean listModified = false;
                    for (Object item : defaultList) {
                        if (!mergedList.contains(item)) {
                            mergedList.add(item);
                            listModified = true;
                            injectedCount++;
                        }
                    }
                    if (listModified) {
                        activeConfig.set(key, mergedList);
                    }
                }
            }
        }

        // Copy header comments if active header is missing
        try {
            List<String> defaultHeader = defaultConfig.options().getHeader();
            List<String> activeHeader = activeConfig.options().getHeader();
            if ((activeHeader == null || activeHeader.isEmpty()) && defaultHeader != null && !defaultHeader.isEmpty()) {
                activeConfig.options().setHeader(defaultHeader);
            }
        } catch (Throwable ignored) {
            // Header handling fallback
        }

        return injectedCount;
    }

    /**
     * Automatically scans and detects all YAML files inside the plugin's resources folder.
     * Excludes 'plugin.yml' and 'paper-plugin.yml'.
     *
     * @param plugin the JavaPlugin instance
     * @return list of relative resource paths for all detected YAML files
     */
    public static List<String> detectAllYamlResources(JavaPlugin plugin) {
        Set<String> resources = new HashSet<>();

        // 1. Scan JAR via CodeSource
        try {
            CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
            if (src != null) {
                URL jarUrl = src.getLocation();
                File jarFile = new File(jarUrl.toURI());
                if (jarFile.isFile()) {
                    try (JarFile jar = new JarFile(jarFile)) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            addIfYamlResource(entry.getName(), resources);
                        }
                    }
                } else {
                    // Exploded directory classpath / IDE development environment
                    scanDirectoryResources(jarFile, jarFile, resources);
                }
            }
        } catch (Exception ignored) {
            // Try fallback via URLConnection
        }

        // 2. Fallback scan via classloader URL connection
        if (resources.isEmpty()) {
            try {
                URL pluginYmlUrl = plugin.getClass().getClassLoader().getResource("plugin.yml");
                if (pluginYmlUrl != null) {
                    URLConnection conn = pluginYmlUrl.openConnection();
                    if (conn instanceof JarURLConnection) {
                        JarURLConnection jarConn = (JarURLConnection) conn;
                        try (JarFile jar = jarConn.getJarFile()) {
                            Enumeration<JarEntry> entries = jar.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry entry = entries.nextElement();
                                addIfYamlResource(entry.getName(), resources);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback scan via streaming CodeSource if File conversion was not possible
        if (resources.isEmpty()) {
            try {
                CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
                if (src != null) {
                    URL jarUrl = src.getLocation();
                    try (ZipInputStream zip = new ZipInputStream(jarUrl.openStream())) {
                        while (true) {
                            ZipEntry entry = zip.getNextEntry();
                            if (entry == null) break;
                            addIfYamlResource(entry.getName(), resources);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return new ArrayList<>(resources);
    }

    private static void addIfYamlResource(String rawPath, Set<String> target) {
        if (rawPath == null || rawPath.isEmpty()) {
            return;
        }

        String path = rawPath.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Exclude plugin descriptors
        if (path.equalsIgnoreCase("plugin.yml") || path.equalsIgnoreCase("paper-plugin.yml")) {
            return;
        }

        if (path.endsWith(".yml") || path.endsWith(".yaml")) {
            target.add(path);
        }
    }

    private static void scanDirectoryResources(File rootDir, File currentDir, Set<String> target) {
        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryResources(rootDir, file, target);
            } else {
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
                addIfYamlResource(relativePath, target);
            }
        }
    }
}
