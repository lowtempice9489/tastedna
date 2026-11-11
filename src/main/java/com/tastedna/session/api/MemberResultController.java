package com.tastedna.session.api;

import com.tastedna.session.application.MemberResultService;
import com.tastedna.session.application.MemberResultCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("member-auth")
@RequestMapping("/api/v1/member/results")
@RequiredArgsConstructor
public class MemberResultController {
    private final MemberResultService service;
    private final MemberResultCleanupService cleanup;
    @GetMapping public MemberResultService.Collection list(Authentication auth) { return service.list(auth.getName()); }
    @GetMapping("/current") public MemberResultService.Entry current(Authentication auth) { return service.list(auth.getName()).current(); }
    @GetMapping("/{id}") public MemberResultService.Entry get(Authentication auth, @PathVariable UUID id) { return service.get(auth.getName(), id); }
    @PostMapping("/{id}/save") public MemberResultService.Entry save(Authentication auth, @PathVariable UUID id) { return service.save(auth.getName(), id); }
    @PostMapping("/{id}/unsave") public MemberResultService.Entry unsave(Authentication auth, @PathVariable UUID id) { return service.unsave(auth.getName(), id); }
    @PostMapping("/{id}/current") public MemberResultService.Entry setCurrent(Authentication auth, @PathVariable UUID id) { return service.setCurrent(auth.getName(), id); }
    @PostMapping("/{id}/delete") public void delete(Authentication auth, @PathVariable UUID id) { cleanup.deleteOwned(auth.getName(), id); }
    @PostMapping("/current/clear") public void clearCurrent(Authentication auth) { service.clearCurrent(auth.getName()); }
}
