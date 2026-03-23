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

        // For VIP players, allow all standard Minecraft color codes but check letter count after stripping
        if (isVip) {
            // Reject anything after & that is not a valid Minecraft color/format code (lowercase only)
            if (tag.contains("&") && !tag.matches("(&[0-9a-fklmnor]|[a-zA-Z0-9])+")) {
                return new ValidationResult(false, configManager.getMessage("tag-vip-invalid-codes"));
            }
            String strippedTag = stripColorCodes(tag);
            int maxLength = configManager.getTagMaxLength();
            if (strippedTag.length() > maxLength) {
                String message = configManager.getMessage("tag-vip-max-length")
                        .replace("%length%", String.valueOf(maxLength));
                return new ValidationResult(false, message);
            }
            if (strippedTag.isEmpty() || !strippedTag.matches("[a-zA-Z0-9]+")) {
                return new ValidationResult(false, configManager.getMessage("tag-invalid-chars"));
            }
        } else {
            // For normal players, no color codes allowed
            if (tag.contains("&")) {
                return new ValidationResult(false, configManager.getMessage("tag-no-colors"));
            }
            // Check length (min–max)
            int minLength = configManager.getTagMinLength();
            int maxLength = configManager.getTagMaxLength();
            if (tag.length() < minLength || tag.length() > maxLength) {
                String message = configManager.getMessage("tag-invalid-length")
                        .replace("%min%", String.valueOf(minLength))
                        .replace("%max%", String.valueOf(maxLength))
                        .replace("%length%", String.valueOf(maxLength));
                return new ValidationResult(false, message);
            }
            // Check if only letters allowed (no numbers)
            if (configManager.isOnlyLettersAllowed()) {
                if (!tag.matches("[a-zA-Z]+")) {
                    return new ValidationResult(false, configManager.getMessage("tag-invalid-chars"));
                }
            } else {
                // Allow letters and numbers
                if (!tag.matches("[a-zA-Z0-9]+")) {
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
        return text.replaceAll("&[0-9a-fklmnor]", "");
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
