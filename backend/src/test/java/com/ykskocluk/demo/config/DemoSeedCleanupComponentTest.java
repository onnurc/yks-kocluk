package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoSeedCleanupComponentTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void whenDemoSeedIsEnabled_cleanupIsNotExecuted() {
        DemoSeedCleanupComponent component = new DemoSeedCleanupComponent(jdbcTemplate, true);

        component.afterSingletonsInstantiated();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void whenDemoSeedIsDisabled_cleanupIsExecuted() {
        DemoSeedCleanupComponent component = new DemoSeedCleanupComponent(jdbcTemplate, false);
        when(jdbcTemplate.update(anyString())).thenReturn(5);

        component.afterSingletonsInstantiated();

        // Verify that the queries are run (there are 10 execute calls and 1 update call)
        verify(jdbcTemplate, times(10)).execute(anyString());
        verify(jdbcTemplate, times(1)).update(anyString());
    }
}
