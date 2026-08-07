package com.sensenav.backend.controller;

import com.sensenav.backend.model.Refuge;
import com.sensenav.backend.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<Refuge> searchRefuges(
            @RequestParam String keyword
    ) {
        return searchService.searchRefuges(keyword);
    }
}