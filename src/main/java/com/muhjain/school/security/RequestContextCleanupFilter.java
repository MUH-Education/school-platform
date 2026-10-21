package com.muhjain.school.security;

import jakarta.servlet.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wraps every request so {@link RequestContext} is always cleared when it ends, even after an
 * error. Runs first (and so finishes last), outside Spring Security's own filters, so nothing
 * in between can skip the cleanup.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextCleanupFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		try {
			chain.doFilter(request, response);
		}
		finally {
			RequestContext.clear();
		}
	}

}
