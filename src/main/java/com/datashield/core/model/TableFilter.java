package com.datashield.core.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enterprise table/collection selective filtering supporting exact matches and glob/regex patterns.
 */
public class TableFilter implements Serializable {
    private final Set<String> includeTables;
    private final Set<String> excludeTables;
    private final Set<Pattern> includePatterns;
    private final Set<Pattern> excludePatterns;

    public TableFilter(Set<String> includes, Set<String> excludes) {
        this.includeTables = includes != null ? new HashSet<>(includes) : Collections.emptySet();
        this.excludeTables = excludes != null ? new HashSet<>(excludes) : Collections.emptySet();
        this.includePatterns = new HashSet<>();
        this.excludePatterns = new HashSet<>();

        for (String inc : this.includeTables) {
            if (inc.contains("*") || inc.contains("?")) {
                this.includePatterns.add(globToPattern(inc));
            }
        }
        for (String exc : this.excludeTables) {
            if (exc.contains("*") || exc.contains("?")) {
                this.excludePatterns.add(globToPattern(exc));
            }
        }
    }

    public static TableFilter all() {
        return new TableFilter(Collections.emptySet(), Collections.emptySet());
    }

    public static TableFilter of(Set<String> includes, Set<String> excludes) {
        return new TableFilter(includes, excludes);
    }

    public boolean shouldInclude(String tableName) {
        if (tableName == null) return false;
        String clean = tableName.trim();

        // Check exclusions first
        if (excludeTables.contains(clean)) return false;
        for (Pattern p : excludePatterns) {
            if (p.matcher(clean).matches()) return false;
        }

        // If no include filter specified, include everything
        if (includeTables.isEmpty() && includePatterns.isEmpty()) {
            return true;
        }

        // Check explicit includes
        if (includeTables.contains(clean)) return true;
        for (Pattern p : includePatterns) {
            if (p.matcher(clean).matches()) return true;
        }

        return false;
    }

    public Set<String> getIncludeTables() {
        return Collections.unmodifiableSet(includeTables);
    }

    public Set<String> getExcludeTables() {
        return Collections.unmodifiableSet(excludeTables);
    }

    public boolean hasFilters() {
        return !includeTables.isEmpty() || !excludeTables.isEmpty();
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> regex.append("\\").append(c);
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}

