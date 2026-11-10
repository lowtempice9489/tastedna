package com.tastedna.session.api;

import com.tastedna.session.api.dto.ApparelScope;
import com.tastedna.session.application.GuestSimpleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@RestController
@Profile("member-auth")
@RequestMapping("/api/v1/guest/simple")
@RequiredArgsConstructor
public class GuestSimpleController {
    private final GuestSimpleService service;
    public record Submission(@NotNull ApparelScope apparelScope, @NotNull @Size(min = 10, max = 10) Map<String, JsonNode> answers) {}
    @GetMapping("/questions")
    public GuestSimpleService.Questions questions(@RequestParam ApparelScope apparelScope) { return service.questions(apparelScope); }
    @PostMapping("/submit")
    public ResponseEntity<GuestSimpleService.Result> submit(@Valid @RequestBody Submission request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.submit(request.apparelScope(), request.answers()));
    }
}
