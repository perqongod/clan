package org.perq.clan;

import java.util.List;

public class TagValidator {
    private final Clan plugin;

    public TagValidator(Clan plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates a clan tag according to configuration rules.
     * @param tag The tag to validate
     * @param isVip Whether the player has VIP permission
     * @return Validation result with error message if invalid
     */
    public ValidationResult validate(String tag, boolean isVip) {
        ConfigManager configManager = plugin.getConfigManager();

        // For VIP players, allow &6 color code but check letter count after stripping
        if (isVip) {
            // Only &6 is permitted – reject any other color/format codes
            if (tag.contains("&")) {
                if (tag.matches(".*&(?![0-9a-fA-FklmnorKLMNOR]).*")) {
                    return new ValidationResult(false, configManager.getMessage("tag-vip-invalid-codes"));
                }
                if (!tag.matches("(&6|[a-zA-Z])+")) {
                    return new ValidationResult(false, configManager.getMessage("tag-vip-only-gold"));
                }
            }
            String strippedTag = stripColorCodes(tag);
            int maxLength = configManager.getTagLength();
            if (strippedTag.length() > maxLength) {
                String message = configManager.getMessage("tag-vip-max-length")
                        .replace("%length%", String.valueOf(maxLength));
                return new ValidationResult(false, message);
            }
            if (strippedTag.isEmpty() || !strippedTag.matches("[a-zA-Z]+")) {
                return new ValidationResult(false, configManager.getMessage("tag-invalid-chars"));
            }
        } else {
            // For normal players, no color codes allowed
            if (tag.contains("&")) {
                return new ValidationResult(false, configManager.getMessage("tag-no-colors"));
            }
            // Check length
            int requiredLength = configManager.getTagLength();
            if (tag.length() != requiredLength) {
                String message = configManager.getMessage("tag-invalid-length")
                        .replace("%length%", String.valueOf(requiredLength));
                return new ValidationResult(false, message);
            }
            // Check if only letters allowed
            if (configManager.isOnlyLettersAllowed()) {
                if (!tag.matches("[a-zA-Z]+")) {
                    return new ValidationResult(false, configManager.getMessage("tag-invalid-chars"));
                }
            }
        }

        // Check blacklist (apply to both, using stripped tag for VIP)
        String checkTag = isVip ? stripColorCodes(tag) : tag;
        List<String> blacklist = configManager.getTagBlacklist();
        if (blacklist != null && blacklist.contains(checkTag.toUpperCase())) {
            return new ValidationResult(false, configManager.getMessage("tag-blacklisted"));
        }

        return new ValidationResult(true, null);
    }

    /**
     * Strips Minecraft color codes from a string
     * @param text The text to strip
     * @return Text without color codes
     */
    private String stripColorCodes(String text) {
        return text.replaceAll("&[0-9a-fA-FklmnorKLMNOR]", "");
    }

    /**
     * Validates a clan tag according to configuration rules (legacy method for non-VIP)
     * @param tag The tag to validate
     * @return Validation result with error message if invalid
     */
    public ValidationResult validate(String tag) {
        return validate(tag, false);
    }

    /**
     * Represents the result of tag validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
