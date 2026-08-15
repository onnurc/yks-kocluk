package com.ykskocluk.demo;

import com.ykskocluk.demo.security.ratelimit.InMemoryRateLimitStore;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Resets the shared {@link InMemoryRateLimitStore} bean before every test method. Spring Test
 * caches and reuses one {@code ApplicationContext} across every {@code @SpringBootTest} class
 * with matching config (all of them here: same annotations, same {@code TestcontainersConfiguration}),
 * so the rate limiter — a singleton in-memory map — otherwise carries state from one test class's
 * login/register calls into the next, tripping 429s that have nothing to do with the test that
 * hits them. Auto-registered for every Spring test via {@code META-INF/spring.factories} rather
 * than added to each of the 40+ affected test classes individually.
 *
 * <p>Deliberately does not touch rate-limit <em>behavior</em> — {@code AuthRateLimitService} and
 * its own dedicated tests (which mock it, or construct their own isolated store instance) are
 * unaffected. This only clears accumulated state between unrelated test classes.
 */
public class RateLimitResetTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        try {
            testContext.getApplicationContext().getBean(InMemoryRateLimitStore.class).clear();
        } catch (NoSuchBeanDefinitionException | IllegalStateException e) {
            // Not every test loads a full context (e.g. @WebMvcTest slices that mock the rate
            // limiter away, or plain unit tests) — nothing to reset there.
        }
    }
}
