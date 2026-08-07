package com.sensenav.backend.controller;

import com.sensenav.backend.model.Warning;
import com.sensenav.backend.service.WarningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warnings")
public class WarningController {

    private final WarningService warningService;

    public WarningController(WarningService warningService) {
        this.warningService = warningService;
    }

    @GetMapping
    public List<Warning> getAllWarnings() {
        return warningService.getAllWarnings();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Warning> getWarningById(@PathVariable Long id) {

        Warning warning = warningService.getWarningById(id);

        if (warning == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(warning);
    }
}