package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.PublicVersionHistoryService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public/resources/{id}/versions")
public class PublicVersionHistoryController {
    private final PublicVersionHistoryService history;
    public PublicVersionHistoryController(PublicVersionHistoryService history){this.history=history;}
    @GetMapping public PublicVersionHistoryService.History list(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return history.list(id,page,size);}
}
