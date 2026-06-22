package dev.yeonwoo.chipthrone.quote.web;

import dev.yeonwoo.chipthrone.quote.service.InvestOpinionService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OpinionController {

    private final InvestOpinionService investOpinionService;

    public OpinionController(InvestOpinionService investOpinionService) {
        this.investOpinionService = investOpinionService;
    }

    @GetMapping("/opinions")
    public OpinionsResponse opinions() {
        return investOpinionService.currentOpinions();
    }
}
