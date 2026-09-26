package fun.endcore.escoins.database;

/**
 * Supported database storage types.
 */
public enum DatabaseType {
    SQLITE,
    MYSQL,
    MARIADB;

    public static DatabaseType fromString(String type) {
        if (type == null) {
            return SQLITE;
        }
        try {
            return valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SQLITE;
        }
    }
}
