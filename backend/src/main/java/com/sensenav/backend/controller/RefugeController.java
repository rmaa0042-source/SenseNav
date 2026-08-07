package com.sensenav.backend.controller;

import com.sensenav.backend.model.Refuge;
import com.sensenav.backend.service.RefugeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refuges")
public class RefugeController {

    private final RefugeService refugeService;

    public RefugeController(RefugeService refugeService) {
        this.refugeService = refugeService;
    }

    @GetMapping
    public List<Refuge> getAllRefuges() {
        return refugeService.getAllRefuges();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Refuge> getRefugeById(@PathVariable Long id) {

        Refuge refuge = refugeService.getRefugeById(id);

        if (refuge == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(refuge);
    }
}