package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.MeetLinkProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JitsiMeetClientLoggingTest {
    @Test
    void infoLogContainsSafeSessionIdentifierButNotRoomUrlOrCredential(CapturedOutput output) {
        String url = new JitsiMeetClient(new MeetLinkProperties(true))
                .createMeetLink(481L, Instant.now(), Instant.now().plusSeconds(1800));

        assertThat(output).contains("meeting created for session 481");
        assertThat(output).doesNotContain(url).doesNotContain(url.substring(url.lastIndexOf('/') + 1));
    }
}
