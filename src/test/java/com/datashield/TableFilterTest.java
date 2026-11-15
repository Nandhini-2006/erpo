package com.datashield;

import com.datashield.core.model.TableFilter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TableFilterTest {

    @Test
    void shouldIncludeAllWhenNoFiltersSpecified() {
        TableFilter filter = TableFilter.all();
        assertThat(filter.shouldInclude("users")).isTrue();
        assertThat(filter.shouldInclude("orders")).isTrue();
    }

    @Test
    void shouldFilterExactIncludesAndExcludes() {
        TableFilter filter = TableFilter.of(Set.of("users", "orders"), Set.of("orders"));
        assertThat(filter.shouldInclude("users")).isTrue();
        assertThat(filter.shouldInclude("orders")).isFalse(); // Exclude overrides include
        assertThat(filter.shouldInclude("transactions")).isFalse(); // Not in includes
    }

    @Test
    void shouldSupportGlobPatterns() {
        TableFilter filter = TableFilter.of(Set.of("user_*", "order"), Set.of("user_temp_*"));
        assertThat(filter.shouldInclude("user_profiles")).isTrue();
        assertThat(filter.shouldInclude("user_credentials")).isTrue();
        assertThat(filter.shouldInclude("user_temp_staging")).isFalse(); // Excluded by glob
        assertThat(filter.shouldInclude("order")).isTrue();
        assertThat(filter.shouldInclude("billing")).isFalse();
    }
}

