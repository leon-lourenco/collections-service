package com.cardbilling.collections.infrastructure.web;

import com.cardbilling.collections.application.RunCollectionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for a collections run — the same entry point the daily schedule uses, exposed so
 * a run can be replayed for a specific date without waiting for tomorrow. Same pattern as the
 * legacy's job trigger endpoints.
 */
@RestController
@RequestMapping("/collections")
@SecurityRequirement(name = "bearer-jwt")
public class CollectionsController {

    private final RunCollectionsUseCase runCollections;
    private final Clock clock;

    public CollectionsController(RunCollectionsUseCase runCollections, Clock clock) {
        this.runCollections = runCollections;
        this.clock = clock;
    }

    @PostMapping("/run")
    @Operation(
            summary = "Run collections for a date",
            description =
                    "Accrues interest on every overdue invoice and escalates each to the notification stage its "
                            + "lateness has reached. Both downstream calls are idempotent, so re-running the same "
                            + "date is safe.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Run completed (possibly on cached invoice data)"),
        @ApiResponse(
                responseCode = "503",
                description = "billing-service is unreachable and nothing is left in the cache")
    })
    public CollectionsRunResponse run(
            @RequestParam(name = "date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        LocalDate asOf = date != null ? date : LocalDate.now(clock);
        return CollectionsRunResponse.from(runCollections.run(asOf));
    }
}
