package com.mindfullinking.core.model

/**
 * Link priority levels 1 through 5.
 * Active links consume attention points matching their priority value (1..5 pts).
 */
enum class Priority(val value: Int) {
    LOWEST(1),
    LOW(2),
    MEDIUM(3),
    HIGH(4),
    HIGHEST(5);

    companion object {
        fun fromInt(value: Int): Priority {
            return entries.find { it.value == value } ?: MEDIUM
        }
    }
}

/**
 * Predefined estimated duration options in minutes for saved links.
 */
enum class DurationOption(val minutes: Int) {
    MIN_1(1),
    MIN_3(3),
    MIN_5(5),
    MIN_10(10),
    MIN_15(15),
    MIN_20(20),
    MIN_30(30),
    MIN_45(45),
    MIN_60(60),
    MIN_90(90),
    MIN_120(120);

    companion object {
        val VALID_MINUTES: List<Int> = entries.map { it.minutes }

        fun fromMinutes(minutes: Int): DurationOption {
            return entries.find { it.minutes == minutes } ?: MIN_15
        }
    }
}

/**
 * User intent categories for a saved link.
 */
enum class IntentCategory {
    READ,
    WATCH,
    ACT,
    REFERENCE,
    OTHER;

    companion object {
        fun fromString(value: String): IntentCategory {
            return runCatching { valueOf(value.uppercase()) }.getOrDefault(OTHER)
        }
    }
}

/**
 * Lifecycle status of a link in Link Ledger.
 */
enum class LinkStatus {
    ACTIVE,
    ARCHIVED,
    RESOLVED;

    companion object {
        fun fromString(value: String): LinkStatus {
            return runCatching { valueOf(value.uppercase()) }.getOrDefault(ACTIVE)
        }
    }
}

/**
 * User-selected reason when resolving an active or archived link.
 */
enum class ResolutionReason {
    COMPLETED,
    APPLIED,
    NO_LONGER_RELEVANT;

    companion object {
        fun fromString(value: String?): ResolutionReason? {
            if (value == null) return null
            return runCatching { valueOf(value.uppercase()) }.getOrNull()
        }
    }
}

/**
 * Subgroup ordering for Inbox deadline grouping.
 */
enum class DeadlineSubgroup(val displayOrder: Int) {
    OVERDUE(1),
    DUE_TODAY(2),
    DUE_WITHIN_3D(3),
    DUE_WITHIN_7D(4),
    DUE_LATER(5),
    NO_DEADLINE(6);

    companion object {
        fun fromOrder(order: Int): DeadlineSubgroup {
            return entries.find { it.displayOrder == order } ?: NO_DEADLINE
        }
    }
}

/**
 * Status of metadata fetching (open-graph tags, page title).
 */
enum class MetadataFetchStatus {
    DISABLED,
    PENDING,
    SUCCESS,
    FAILED;

    companion object {
        fun fromString(value: String): MetadataFetchStatus {
            return runCatching { valueOf(value.uppercase()) }.getOrDefault(DISABLED)
        }
    }
}

/**
 * Intake mechanism for how the link was saved.
 */
enum class CreatedFrom {
    SHARE,
    MANUAL;

    companion object {
        fun fromString(value: String): CreatedFrom {
            return runCatching { valueOf(value.uppercase()) }.getOrDefault(MANUAL)
        }
    }
}

/**
 * Event types logged for audit and analytics redaction.
 */
enum class LinkEventType {
    SAVE,
    ARCHIVE,
    RESOLVE,
    RESTORE,
    DELETE,
    SKIP,
    SNOOZE,
    OPEN;

    companion object {
        fun fromString(value: String): LinkEventType {
            return runCatching { valueOf(value.uppercase()) }.getOrDefault(SAVE)
        }
    }
}
