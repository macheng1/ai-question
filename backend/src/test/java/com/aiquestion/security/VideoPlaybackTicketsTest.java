package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.config.JwtProperties;
import java.time.Clock;
import static com.aiquestion.security.VideoPlaybackTickets.Source.COURSE_LEARNING;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VideoPlaybackTicketsTest {
    private final JwtProperties properties = new JwtProperties("test-secret-012345678901234567890123456789",
        "1h", "refresh-secret-012345678901234567890123456789", "1h");

    @Test void ticketOnlyGrantsAccessToTheIssuedVideo() throws Exception {
        var tickets = new VideoPlaybackTickets(properties);
        String ticket = tickets.issue(12, 23, COURSE_LEARNING);
        assertEquals(new VideoPlaybackTickets.Grant(12, COURSE_LEARNING), tickets.verify(23, ticket));
        assertNotEquals(ticket, tickets.issue(12, 23, COURSE_LEARNING));
        assertThrows(BusinessException.class, () -> tickets.verify(24, ticket));
        assertThrows(BusinessException.class, () -> tickets.verify(23, "invalid"));
        var accounts = new JwtTokenService(properties);
        assertThrows(RuntimeException.class, () -> accounts.subject(ticket, false));
        assertThrows(BusinessException.class, () -> tickets.verify(23, accounts.issue("23").accessToken()));
    }

    @Test void expiredTicketCannotOpenVideo() throws Exception {
        var tickets = new VideoPlaybackTickets(properties);
        String expired = tickets.issue(12, 23, COURSE_LEARNING, Clock.offset(Clock.systemUTC(), Duration.ofHours(-3)));
        assertThrows(BusinessException.class, () -> tickets.verify(23, expired));
    }
}
