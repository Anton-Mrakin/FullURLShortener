package com.mrakin.domain.event;

import com.mrakin.domain.model.Url;
import org.springframework.modulith.events.Externalized;

/**
 * Event published when a URL is accessed.
 */
@Externalized("url-accessed")
public record UrlAccessedEvent(Url url) {
}
