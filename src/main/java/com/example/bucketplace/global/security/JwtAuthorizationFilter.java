package com.example.bucketplace.global.security;

import com.example.bucketplace.global.exception.ErrorCode;
import com.example.bucketplace.global.jwt.TokenProvider;
import com.example.bucketplace.global.util.CustomResponseUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final UserDetailsServiceImpl userDetailsService;
    private final TokenProvider tokenProvider;

    public JwtAuthorizationFilter(UserDetailsServiceImpl userDetailsService, TokenProvider tokenProvider) {
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = tokenProvider.getAccessTokenFromHeader(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            tokenProvider.isExpired(accessToken);
        } catch (ExpiredJwtException e) {
            CustomResponseUtil.fail(response, ErrorCode.EXPIRED_ACCESS_TOKEN.getMessage(), HttpStatus.UNAUTHORIZED);
            return;
        }

        String tokenType = tokenProvider.getTokenType(accessToken);
        if (!tokenType.equals("access")) {
            CustomResponseUtil.fail(response, ErrorCode.INVALID_ACCESS_TOKEN.getMessage(), HttpStatus.UNAUTHORIZED);
            return;
        }

        Claims claims = tokenProvider.getMemberInfoFromToken(accessToken);

        try {
            setAuthentication(claims.get("email").toString());
        } catch (Exception e) {
            log.error(e.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    public void setAuthentication(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = createAuthentication(email);
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }

    private Authentication createAuthentication(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
