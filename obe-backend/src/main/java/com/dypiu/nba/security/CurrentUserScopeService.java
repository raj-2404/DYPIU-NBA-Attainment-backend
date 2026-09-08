package com.dypiu.nba.security;

import com.dypiu.nba.entity.User;
import com.dypiu.nba.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * Centralized backend service to securely resolve the authenticated user's organizational scope.
 *
 * The scope is ALWAYS resolved directly from the authenticated User entity in the database.
 * Frontend query parameters (such as ?schoolId=...) are never trusted for scope resolution.
 * Scope resolution is cached in HTTP request attributes to eliminate duplicate DB lookups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserScopeService {

    private static final String REQUEST_SCOPE_ATTRIBUTE = "CACHED_CURRENT_USER_SCOPE";
    private static final String REQUEST_USER_ATTRIBUTE = "CACHED_CURRENT_USER_ENTITY";

    private final UserRepository userRepository;

    /**
     * Resolves the CurrentUserScope from the active Spring SecurityContext.
     * Caches the resolved scope for the lifetime of the HTTP request to prevent repeated DB roundtrips.
     *
     * @return Immutable CurrentUserScope containing verified user and scope metadata.
     * @throws ResponseStatusException HTTP 401 if unauthenticated or user record not found; HTTP 403 if deactivated.
     */
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public CurrentUserScope getCurrentUserScope() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            CurrentUserScope cached = (CurrentUserScope) attrs.getAttribute(REQUEST_SCOPE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) {
                return cached;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUserScope scope = resolveScopeFromAuthentication(authentication);
        if (attrs != null && scope != null) {
            attrs.setAttribute(REQUEST_SCOPE_ATTRIBUTE, scope, RequestAttributes.SCOPE_REQUEST);
        }
        return scope;
    }

    /**
     * Resolves the CurrentUserScope using an injected Principal or falls back to SecurityContext.
     *
     * @param principal Principal injected into controller method.
     * @return Immutable CurrentUserScope.
     */
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public CurrentUserScope getCurrentUserScope(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                CurrentUserScope cached = (CurrentUserScope) attrs.getAttribute(REQUEST_SCOPE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
                if (cached != null && (principal.getName().equalsIgnoreCase(cached.getUsername()) || principal.getName().equalsIgnoreCase(cached.getEmail()))) {
                    return cached;
                }
            }
            CurrentUserScope scope = resolveScopeByIdentifier(principal.getName());
            if (attrs != null && scope != null) {
                attrs.setAttribute(REQUEST_SCOPE_ATTRIBUTE, scope, RequestAttributes.SCOPE_REQUEST);
            }
            return scope;
        }
        return getCurrentUserScope();
    }

    /**
     * Resolves the CurrentUserScope from an explicit Authentication object.
     *
     * @param authentication Authentication from Spring Security.
     * @return Immutable CurrentUserScope.
     */
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public CurrentUserScope getCurrentUserScope(Authentication authentication) {
        return resolveScopeFromAuthentication(authentication);
    }

    /**
     * Retrieves the active User entity for the current authenticated principal.
     * Caches the resolved entity for the lifetime of the HTTP request.
     *
     * @return Managed User entity.
     * @throws ResponseStatusException HTTP 401 if unauthenticated or user not found.
     */
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public User getCurrentUser() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            User cached = (User) attrs.getAttribute(REQUEST_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) {
                return cached;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = resolveUserFromAuthentication(authentication);
        if (attrs != null && user != null) {
            attrs.setAttribute(REQUEST_USER_ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
        }
        return user;
    }

    /**
     * Converts a known User entity into an immutable CurrentUserScope.
     *
     * @param user User entity.
     * @return CurrentUserScope instance.
     */
    public CurrentUserScope fromUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User entity is null");
        }

        com.dypiu.nba.entity.UserRole effectiveRole = user.getRole();
        String effectiveSchoolId = user.getSchoolId();
        String effectiveDepartmentId = user.getDepartmentId();
        String effectiveMasterProgrammeId = user.getMasterProgrammeId();

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String activeRoleStr = (String) attrs.getAttribute("ACTIVE_ROLE_OVERRIDE", RequestAttributes.SCOPE_REQUEST);
            if (activeRoleStr != null && !activeRoleStr.isBlank()) {
                effectiveRole = mapStringToUserRole(activeRoleStr, user.getRole());
            }
            String activeSchool = (String) attrs.getAttribute("ACTIVE_SCHOOL_OVERRIDE", RequestAttributes.SCOPE_REQUEST);
            if (activeSchool != null && !activeSchool.isBlank()) {
                effectiveSchoolId = activeSchool;
            }
            String activeDept = (String) attrs.getAttribute("ACTIVE_DEPT_OVERRIDE", RequestAttributes.SCOPE_REQUEST);
            if (activeDept != null && !activeDept.isBlank()) {
                effectiveDepartmentId = activeDept;
            }
            String activeProg = (String) attrs.getAttribute("ACTIVE_PROG_OVERRIDE", RequestAttributes.SCOPE_REQUEST);
            if (activeProg != null && !activeProg.isBlank()) {
                effectiveMasterProgrammeId = activeProg;
            }
        }

        return CurrentUserScope.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .role(effectiveRole)
                .schoolId(effectiveSchoolId)
                .departmentId(effectiveDepartmentId)
                .masterProgrammeId(effectiveMasterProgrammeId)
                .build();
    }

    private com.dypiu.nba.entity.UserRole mapStringToUserRole(String roleStr, com.dypiu.nba.entity.UserRole fallback) {
        if (roleStr == null || roleStr.isBlank()) return fallback;
        String clean = roleStr.toUpperCase().replace("ROLE_", "").trim();
        if (clean.equals("COURSE_COORDINATOR") || clean.equals("CC") || clean.equals("FACULTY") || clean.equals("TEACHER")) {
            return com.dypiu.nba.entity.UserRole.FACULTY;
        }
        if (clean.equals("PROGRAMME_COORDINATOR") || clean.equals("PC") || clean.equals("COORDINATOR")) {
            return com.dypiu.nba.entity.UserRole.PROGRAMME_COORDINATOR;
        }
        if (clean.equals("HOD") || clean.equals("HEAD_OF_DEPARTMENT")) {
            return com.dypiu.nba.entity.UserRole.HOD;
        }
        if (clean.equals("DIRECTOR") || clean.equals("DEAN")) {
            return com.dypiu.nba.entity.UserRole.DIRECTOR;
        }
        if (clean.equals("IQAC") || clean.equals("ADMIN")) {
            return com.dypiu.nba.entity.UserRole.IQAC;
        }
        try {
            return com.dypiu.nba.entity.UserRole.valueOf(clean);
        } catch (Exception e) {
            return fallback;
        }
    }

    private CurrentUserScope resolveScopeFromAuthentication(Authentication authentication) {
        User user = resolveUserFromAuthentication(authentication);
        return fromUser(user);
    }

    private User resolveUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user found in security context");
        }

        String identifier = authentication.getName();
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated principal has empty identifier");
        }

        return resolveUserByIdentifier(identifier);
    }

    private CurrentUserScope resolveScopeByIdentifier(String identifier) {
        User user = resolveUserByIdentifier(identifier);
        return fromUser(user);
    }

    private User resolveUserByIdentifier(String identifier) {
        String cleanIdentifier = identifier.trim();
        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(cleanIdentifier, cleanIdentifier)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(cleanIdentifier, cleanIdentifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found in database for identifier: " + cleanIdentifier)));

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "User account is deactivated: " + cleanIdentifier);
        }

        return user;
    }
}
